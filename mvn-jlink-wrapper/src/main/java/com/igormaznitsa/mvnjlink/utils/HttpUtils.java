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

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.common.utils.GetUtils;
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
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.protocol.HttpContext;
import org.apache.maven.plugin.logging.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.function.Consumer;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.of;
import org.apache.http.entity.ContentType;

public final class HttpUtils {

  public static final String MIME_OCTET_STREAM = "application/octet-stream";

  private HttpUtils() {

  }

  @Nonnull
  @MustNotContainNull
  public static Header[] doGetRequest(
          @Nonnull final HttpClient client,
          @Nonnull final String urlLink,
          @Nullable final ProxySettings proxySettings,
          @Nonnull final Consumer<HttpEntity> consumer,
          final int timeout,
          final boolean allowOctetStream,
          @Nonnull @MustNotContainNull String... acceptedContent
  ) throws IOException {

    final RequestConfig.Builder config = RequestConfig
            .custom()
            .setSocketTimeout(timeout)
            .setConnectTimeout(timeout);

    if (proxySettings != null) {
      final HttpHost proxyHost = new HttpHost(proxySettings.host, proxySettings.port, proxySettings.protocol);
      config.setProxy(proxyHost);
    }

    final HttpGet methodGet = new HttpGet(urlLink);

    if (allowOctetStream) {
      if (Arrays.stream(acceptedContent).noneMatch(x -> x.trim().equalsIgnoreCase(MIME_OCTET_STREAM))) {
        acceptedContent = Arrays.copyOf(acceptedContent, acceptedContent.length + 1);
        acceptedContent[acceptedContent.length - 1] = MIME_OCTET_STREAM;
      }
    }

    if (acceptedContent.length != 0) {
      methodGet.addHeader("Accept", of(acceptedContent).collect(joining(", ")));
    }

    methodGet.setConfig(config.build());

    final HttpResponse response = client.execute(methodGet);
    try {
      final StatusLine statusLine = response.getStatusLine();

      if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
        throw new IOException(String.format("Can't doLoad SDK archive from %s : %d %s", urlLink, statusLine.getStatusCode(), statusLine.getReasonPhrase()));
      }

      final HttpEntity entity = response.getEntity();
      final ContentTypeParsed contentType = new ContentTypeParsed(ContentType.get(entity).getMimeType());

      if (acceptedContent.length != 0 && of(acceptedContent).map(ContentTypeParsed::new).noneMatch(x -> x.equals(contentType))) {
        throw new IOException("Unexpected content type : " + ContentType.get(entity).getMimeType() + " (expected: " + Arrays.toString(acceptedContent) + ")");
      }

      consumer.accept(entity);

    } finally {
      methodGet.releaseConnection();
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
  public static HttpClient makeHttpClient(@Nonnull Log logger, @Nullable final ProxySettings proxy, final boolean disableSslCheck) throws IOException {
    final HttpClientBuilder builder = HttpClients.custom();

    builder.disableCookieManagement();

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
        sslcontext.init(null, new TrustManager[]{tm}, null);

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
    return builder.build();
  }

}
