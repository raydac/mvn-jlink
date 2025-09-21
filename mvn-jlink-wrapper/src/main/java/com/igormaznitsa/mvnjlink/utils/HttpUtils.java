/*
 * Copyright 2019 Igor Maznitsa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.igormaznitsa.mvnjlink.utils;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.of;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.common.utils.GetUtils;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.protocol.HttpContext;
import org.apache.maven.plugin.logging.Log;

public final class HttpUtils {

  public static final String MIME_ALL = ContentType.WILDCARD.getMimeType();
  public static final String MIME_OCTET_STREAM = "application/octet-stream";
  public static final Set<String> ARCHIVE_MIME_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                  "binary/octet-stream",
                  "application/x-gzip",
                  "application/zip",
                  "application/tar+gzip"
          ))
  );

  private HttpUtils() {

  }

  @Nonnull
  private static HttpRequestBase makeGet(
      @Nonnull final String urlLink,
      @Nonnull RequestConfig config,
      @Nullable final Function<HttpRequestBase, HttpRequestBase> customizer,
      @Nonnull @MustNotContainNull String... acceptedContent
  ) {
    HttpRequestBase methodGet = new HttpGet(urlLink);
    if (customizer != null) {
      methodGet = customizer.apply(methodGet);
    }

    if (acceptedContent.length != 0) {
      methodGet.addHeader("Accept", of(acceptedContent).collect(joining(", ")));
    }

    methodGet.setHeader("User-Agent", "mvn-jlink-plugin");
    methodGet.setConfig(config);

    return methodGet;
  }

  @Nonnull
  @MustNotContainNull
  public static Header[] doGetRequest(
      @Nonnull final HttpClient client,
      @Nullable final Function<HttpRequestBase, HttpRequestBase> customizer,
      @Nonnull final String urlLink,
      @Nullable final ProxySettings proxySettings,
      @Nullable final Consumer<HttpResponse> responseConsumer,
      @Nonnull final Consumer<HttpEntity> consumer,
      final int timeout,
      final boolean expectedBinaryFile,
      @Nonnull @MustNotContainNull String... acceptedContent
  ) throws IOException {

    if (expectedBinaryFile) {
      if (Arrays.stream(acceptedContent).noneMatch(x -> x.trim().equalsIgnoreCase(MIME_OCTET_STREAM))) {
        acceptedContent = Arrays.copyOf(acceptedContent, acceptedContent.length + 1);
        acceptedContent[acceptedContent.length - 1] = MIME_OCTET_STREAM;
      }
    }

    final RequestConfig.Builder configBuilder = RequestConfig
        .custom()
        .setRedirectsEnabled(true)
        .setSocketTimeout(timeout)
        .setConnectTimeout(timeout);

    if (proxySettings != null) {
      final HttpHost proxyHost = new HttpHost(proxySettings.host, proxySettings.port, proxySettings.protocol);
      configBuilder.setProxy(proxyHost);
    }

    RequestConfig config = configBuilder.build();

    HttpResponse response = null;
    HttpRequestBase methodGet = null;
    try {
      int statusCode = -1;
      StatusLine statusLine = null;

      for (int i = 0; i < 5; i++) {
        methodGet = makeGet(urlLink, config, customizer, acceptedContent);
        response = client.execute(methodGet);
        statusLine = response.getStatusLine();
        statusCode = statusLine.getStatusCode();

        if (statusCode == HttpStatus.SC_GATEWAY_TIMEOUT) {
          methodGet.releaseConnection();
          try {
            Thread.sleep(10000L);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        } else {
          break;
        }
      }

      if (statusCode != HttpStatus.SC_OK) {
        throw new HttpResponseException(
            String.format("HTTP request returns unexpected %d code (%s)",
                response.getStatusLine().getStatusCode(),
                response.getStatusLine().getReasonPhrase()), response);
      }

      if (responseConsumer != null) {
        responseConsumer.accept(response);
      }

      final HttpEntity entity = response.getEntity();
      final ContentTypeParsed contentType = Optional.ofNullable(ContentType.get(entity)).map(ContentType::getMimeType).map(ContentTypeParsed::new).orElse(null);

      if (acceptedContent.length != 0 && Arrays.stream(acceptedContent).noneMatch(MIME_ALL::equals) && Arrays.stream(acceptedContent).map(ContentTypeParsed::new).noneMatch(x -> x.equals(contentType))) {
        if (!expectedBinaryFile || ARCHIVE_MIME_TYPES.stream().map(ContentTypeParsed::new).noneMatch(x -> x.equals(contentType))) {
          throw new IOException("Unexpected content type : " + ContentType.get(entity) + " (expected: " + Arrays.toString(acceptedContent) + ")");
        }
      }
      consumer.accept(entity);
    } finally {
      if (methodGet != null) {
        methodGet.releaseConnection();
      }
    }
    return response.getAllHeaders();
  }

  @Nonnull
  public static String extractComputerName() {
    String result = System.getenv("COMPUTERNAME");
    if (result == null) {
      result = System.getenv("HOSTNAME");
    }
    if (result == null) {
      try {
        result = InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException ex) {
        result = null;
      }
    }
    return GetUtils.ensureNonNull(result, "<Unknown computer>");
  }

  @Nonnull
  public static String extractDomainName() {
    final String result = System.getenv("USERDOMAIN");
    return GetUtils.ensureNonNull(result, "");
  }

  @Nonnull
  public static HttpClient makeHttpClient(
      @Nonnull Log logger,
      @Nullable final ProxySettings proxy,
      final boolean disableSslCheck
  ) throws IOException {
    return makeHttpClient(logger, proxy, x -> x, disableSslCheck);
  }

  @Nonnull
  public static HttpClient makeHttpClient(
      @Nonnull Log logger,
      @Nullable final ProxySettings proxy,
      @Nullable final Function<HttpClientBuilder, HttpClientBuilder> tuner,
      final boolean disableSslCheck
  ) throws IOException {
    final HttpClientBuilder builder = HttpClients.custom();

    builder
        .setRedirectStrategy(LaxRedirectStrategy.INSTANCE)
        .disableCookieManagement();

    if (proxy != null) {
      if (proxy.hasCredentials()) {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(proxy.host, proxy.port),
            new NTCredentials(GetUtils.ensureNonNull(proxy.username, ""), proxy.password, extractComputerName(), extractDomainName()));
        builder.setDefaultCredentialsProvider(credentialsProvider);
        logger.debug(String.format("Credentials provider has been created for proxy (username : %s): %s", proxy.username, proxy.toString()));
      }

      final String[] ignoreForAddresses = proxy.nonProxyHosts == null ? new String[0] : proxy.nonProxyHosts.split("\\|");

      final WildCardMatcher[] matchers;

      if (ignoreForAddresses.length > 0) {
        matchers = new WildCardMatcher[ignoreForAddresses.length];
        for (int i = 0; i < ignoreForAddresses.length; i++) {
          matchers[i] = new WildCardMatcher(ignoreForAddresses[i], true);
        }
      } else {
        matchers = new WildCardMatcher[0];
      }

      logger.debug("Regular routing mode");

      final HttpRoutePlanner routePlanner = new DefaultProxyRoutePlanner(new HttpHost(proxy.host, proxy.port, proxy.protocol)) {
        @Nonnull
        @Override
        public HttpRoute determineRoute(@Nonnull final HttpHost host, @Nonnull final HttpRequest request, @Nonnull final HttpContext context) throws HttpException {
          HttpRoute result = null;
          final String hostName = host.getHostName();
          for (final WildCardMatcher m : matchers) {
            if (m.match(hostName)) {
              logger.debug("Ignoring proxy for host : " + hostName);
              result = new HttpRoute(host);
              break;
            }
          }
          if (result == null) {
            result = super.determineRoute(host, request, context);
          }
          logger.debug("Made connection route : " + result);
          return result;
        }
      };

      builder.setRoutePlanner(routePlanner);
      logger.debug("Proxy will ignore: " + Arrays.toString(matchers));
    }

    builder.setUserAgent("mvn-jlink-agent/1.0");

    if (disableSslCheck) {
      try {
        final SSLContext sslcontext = SSLContext.getInstance("TLS");
        X509TrustManager tm = new X509TrustManager() {
          @Override
          @Nullable
          @MustNotContainNull
          public X509Certificate[] getAcceptedIssuers() {
            return null;
          }

          @Override
          public void checkClientTrusted(@Nonnull @MustNotContainNull final X509Certificate[] arg0, @Nonnull final String arg1) throws CertificateException {
            // do nothing
          }

          @Override
          public void checkServerTrusted(@Nonnull @MustNotContainNull final X509Certificate[] arg0, @Nonnull String arg1) throws CertificateException {
            // do nothing
          }
        };
        sslcontext.init(null, new TrustManager[] {tm}, null);

        final SSLConnectionSocketFactory sslfactory = new SSLConnectionSocketFactory(sslcontext, NoopHostnameVerifier.INSTANCE);
        final Registry<ConnectionSocketFactory> r = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("https", sslfactory)
            .register("http", new PlainConnectionSocketFactory()).build();

        builder.setConnectionManager(new BasicHttpClientConnectionManager(r));
        builder.setSSLSocketFactory(sslfactory);
        builder.setSSLContext(sslcontext);

        logger.warn("SSL certificate check has been disabled");
      } catch (final Exception ex) {
        throw new IOException("Can't disable SSL certificate check", ex);
      }
    }

    return tuner == null ? builder.build() : tuner.apply(builder).build();
  }

}
