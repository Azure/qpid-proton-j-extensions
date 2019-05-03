package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.ProxyChallengeProcessor;

import java.net.*;
import java.util.*;

public class BasicProxyChallengeProcessorImpl implements ProxyChallengeProcessor {

    private final ProxyAuthenticator proxyAuthenticator;
    private final Map<String, String> headers;
    private String host;

    BasicProxyChallengeProcessorImpl(String host) {
        this.host = host;
        headers = new HashMap<>();
        proxyAuthenticator = new ProxyAuthenticator();
    }

    @Override
    public Map<String, String> getHeader() {
        PasswordAuthentication passwordAuthentication =
                proxyAuthenticator.getPasswordAuthentication(Constants.BASIC, host);

        if (!ProxyAuthenticator.isPasswordAuthenticationHasValues(passwordAuthentication)) {
            return null;
        }

        String proxyUserName = passwordAuthentication.getUserName();
        String proxyPassword = new String(passwordAuthentication.getPassword());
        final String usernamePasswordPair = proxyUserName + ":" + proxyPassword;

        headers.put(
                Constants.PROXY_AUTHORIZATION,
                String.join(" ", Constants.BASIC, Base64.getEncoder().encodeToString(usernamePasswordPair.getBytes())));
        return headers;
    }
}
