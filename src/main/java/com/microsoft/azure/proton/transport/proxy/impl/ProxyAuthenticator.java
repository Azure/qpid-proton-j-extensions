package com.microsoft.azure.proton.transport.proxy.impl;

import java.net.*;
import java.util.List;
import java.util.Objects;

/**
 * Responds to proxy challenge requests by providing authentication information.
 */
class ProxyAuthenticator {
    private static final String PROMPT = "Event Hubs client web socket proxy support";

    private final PasswordAuthentication passwordAuthentication;

    /**
     * Creates an authenticator that authenticates using system-configured authenticator.
     */
    ProxyAuthenticator() {
        this.passwordAuthentication = null;
    }

    /**
     * Creates an authenticator that responses to authentication requests with the provided username and password.
     * @param username Username for authentication challenge.
     * @param password Password for authentication challenge.
     */
    ProxyAuthenticator(String username, char[] password) {
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);

        passwordAuthentication = new PasswordAuthentication(username, password);
    }

    /**
     * Gets the credentials to use for proxy authentication given the {@code scheme} and {@code host}. If
     * {@link ProxyAuthenticator#ProxyAuthenticator(String, char[])} was used to construct this instance, it is always
     * returned.
     *
     * @param scheme The authentication scheme for the proxy.
     * @param host The proxy's URL that is requesting authentication.
     * @return The username and password to authenticate against proxy with.
     */
    PasswordAuthentication getPasswordAuthentication(String scheme, String host) {
        if (passwordAuthentication != null) {
            return passwordAuthentication;
        }

        ProxySelector proxySelector = ProxySelector.getDefault();

        URI uri;
        List<Proxy> proxies = null;
        if (!StringUtils.isNullOrEmpty(host)) {
            uri = URI.create(host);
            proxies = proxySelector.select(uri);
        }

        InetAddress proxyAddr = null;
        java.net.Proxy.Type proxyType = null;
        if (isProxyAddressLegal(proxies)) {
            // will be only one element in the proxy list
            proxyAddr = ((InetSocketAddress)proxies.get(0).address()).getAddress();
            proxyType = proxies.get(0).type();
        }

        // It appears to be fine to pass in a null value for proxyAddr and proxyType (which maps to "scheme" argument in
        // the call to requestPasswordAuthentication).
        return Authenticator.requestPasswordAuthentication(
                "",
                proxyAddr,
                0,
                proxyType == null ? "" : proxyType.name(),
                PROMPT,
                scheme,
                null,
                Authenticator.RequestorType.PROXY);
    }

    static boolean isPasswordAuthenticationHasValues(PasswordAuthentication passwordAuthentication) {
        if (passwordAuthentication == null) {
            return false;
        }

        final String username = passwordAuthentication.getUserName();
        final char[] password = passwordAuthentication.getPassword();

        return !StringUtils.isNullOrEmpty(username) && password != null && password.length > 0;
    }

    private static boolean isProxyAddressLegal(final List<Proxy> proxies) {
        return proxies != null
                && !proxies.isEmpty()
                && proxies.get(0).address() != null
                && proxies.get(0).address() instanceof InetSocketAddress;
    }
}
