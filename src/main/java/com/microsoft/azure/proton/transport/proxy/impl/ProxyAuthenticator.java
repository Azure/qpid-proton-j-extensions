package com.microsoft.azure.proton.transport.proxy.impl;

import java.net.*;
import java.util.List;

public class ProxyAuthenticator {

    public PasswordAuthentication getPasswordAuthentication(String scheme, String host) {
        ProxySelector proxySelector = ProxySelector.getDefault();

        URI uri;
        List<Proxy> proxies = null;
        if (host != null && !host.isEmpty()) {
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
                "Event Hubs client websocket proxy support",
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
