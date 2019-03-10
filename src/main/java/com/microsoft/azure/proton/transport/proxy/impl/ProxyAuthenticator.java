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

    public boolean isPasswordAuthenticationHasValues(PasswordAuthentication passwordAuthentication){
        if (passwordAuthentication == null) return false;
        String proxyUserName = passwordAuthentication.getUserName() != null
                ? passwordAuthentication.getUserName()
                : null ;
        String proxyPassword = passwordAuthentication.getPassword() != null
                ? new String(passwordAuthentication.getPassword())
                : null;
        if (isNullOrEmpty(proxyUserName) || isNullOrEmpty(proxyPassword))  return false;
        return true;
    }

    private boolean isProxyAddressLegal(final List<Proxy> proxies) {
        return proxies != null
                && !proxies.isEmpty()
                && proxies.get(0).address() != null
                && proxies.get(0).address() instanceof InetSocketAddress;
    }

    private boolean isNullOrEmpty(String string) {
        return (string == null || string.isEmpty());
    }

}
