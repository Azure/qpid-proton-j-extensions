package com.microsoft.azure.proton.transport.proxy.impl;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.Arrays;

/**
 * Test authenticator we can use to test what fields are set when calling
 * {@link Authenticator#requestPasswordAuthentication(String, InetAddress, int, String, String, String)}.
 */
class TestAuthenticator extends Authenticator implements AutoCloseable {
    private final PasswordAuthentication passwordAuthentication;

    TestAuthenticator(String username, String password) {
        passwordAuthentication = new PasswordAuthentication(username, password.toCharArray());
    }

    String requestingHost() {
        return getRequestingHost();
    }

    InetAddress requestingSite() {
        return getRequestingSite();
    }

    int requestingPort() {
        return getRequestingPort();
    }

    String requestingProtocol() {
        return getRequestingProtocol();
    }

    String requestingPrompt() {
        return getRequestingPrompt();
    }

    String requestingScheme() {
        return getRequestingScheme();
    }

    URL requestingURL() {
        return getRequestingURL();
    }

    RequestorType requestorType() {
        return getRequestorType();
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        return passwordAuthentication;
    }

    @Override
    public void close() {
        if (passwordAuthentication != null) {
            Arrays.fill(passwordAuthentication.getPassword(), '\0');
        }
    }
}
