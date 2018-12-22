package com.igormaznitsa.mvnjlink.utils;

import org.apache.maven.settings.Proxy;

import javax.annotation.Nonnull;

public class ProxySettings {

  /**
   * The proxy host.
   */
  public String host = "127.0.0.1";

  /**
   * The proxy protocol.
   */
  public String protocol = "http";

  /**
   * The proxy port.
   */
  public int port = 80;

  /**
   * The proxy user.
   */
  public String username;

  /**
   * The proxy password.
   */
  public String password = "";

  /**
   * The list of non-proxied hosts (delimited by |).
   */
  public String nonProxyHosts;

  public ProxySettings() {
  }

  public ProxySettings(@Nonnull final Proxy mavenProxy) {
    this.protocol = mavenProxy.getProtocol();
    this.host = mavenProxy.getHost();
    this.port = mavenProxy.getPort();
    this.username = mavenProxy.getUsername();
    this.password = mavenProxy.getPassword();
    this.nonProxyHosts = mavenProxy.getNonProxyHosts();
  }

  public boolean hasCredentials() {
    return this.username != null && this.password != null;
  }

  @Override
  @Nonnull
  public String toString() {
    return this.protocol + "://" + this.host + ":" + this.port;
  }
}
