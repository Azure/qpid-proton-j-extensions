package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.ProxyChallengeProcessor;

import java.net.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class BasicProxyChallengeProcessorImpl implements ProxyChallengeProcessor {

    private final ProxyAuthenticator proxyAuthenticator;
    private final Map<String, String> headers;
    private String host;

    BasicProxyChallengeProcessorImpl(String host, ProxyAuthenticator proxyAuthenticator) {
        Objects.requireNonNull(host);
        Objects.requireNonNull(proxyAuthenticator);

        this.host = host;
        headers = new HashMap<>();
        this.proxyAuthenticator = proxyAuthenticator;
    }

    @Override
    public Map<String, String> getHeader() {
        PasswordAuthentication passwordAuthentication =
                proxyAuthenticator.getPasswordAuthentication(Constants.BASIC_LOWERCASE, host);

        if (!ProxyAuthenticator.isPasswordAuthenticationHasValues(passwordAuthentication)) {
            return null;
        }

        final String proxyUserName = passwordAuthentication.getUserName();
        final String proxyPassword = new String(passwordAuthentication.getPassword());
        final String usernamePasswordPair = String.join(":", proxyUserName, proxyPassword);

        headers.put(
                Constants.PROXY_AUTHORIZATION,
                String.join(" ", Constants.BASIC, Base64.getEncoder().encodeToString(usernamePasswordPair.getBytes(UTF_8))));
        return headers;
    }
}
