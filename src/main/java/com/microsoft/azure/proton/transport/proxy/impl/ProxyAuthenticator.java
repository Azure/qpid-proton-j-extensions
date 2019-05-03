package com.microsoft.azure.proton.transport.proxy.impl;

import java.net.*;
import java.util.List;
import java.util.Objects;

/**
 * Responds to proxy challenge requests by providing authentication information.
 */
class ProxyAuthenticator {
    private static final String PROMPT = "Event Hubs client web socket proxy support";

    /**
     * Creates an authenticator that authenticates using system-configured authenticator.
     */
    ProxyAuthenticator() {
    }

    /**
     * Gets the credentials to use for proxy authentication given the {@code scheme} and {@code host}.
     *
     * @param scheme The authentication scheme for the proxy.
     * @param host The proxy's URL that is requesting authentication.
     * @return The username and password to authenticate against proxy with.
     */
    PasswordAuthentication getPasswordAuthentication(String scheme, String host) {
        ProxySelector proxySelector = ProxySelector.getDefault();

        URI uri;
        List<Proxy> proxies = null;
        if (!isNullOrEmpty(host)) {
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

        return !isNullOrEmpty(username) && password != null && password.length > 0;
    }

    private static boolean isProxyAddressLegal(final List<Proxy> proxies) {
        return proxies != null
                && !proxies.isEmpty()
                && proxies.get(0).address() != null
                && proxies.get(0).address() instanceof InetSocketAddress;
    }

    private static boolean isNullOrEmpty(String string) {
        return (string == null || string.isEmpty());
    }
}
