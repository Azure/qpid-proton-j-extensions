package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.ProxyAuthenticationType;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Objects;

public class ProxyConfiguration {
    private final String proxyAddress;
    private final ProxyAuthenticationType authentication;
    private final PasswordAuthentication credentials;

    /**
     * Creates a proxy configuration that uses the {@code proxyAddress} and authenticates with provided
     * {@code username}, {@code password} and {@code authentication}.
     *
     * @param proxyAddress URL of the proxy. If {@code null} is passed in, then the system configured proxy url is used.
     * @param username Username used to authenticate with proxy. Optional if {@code authentication} is
     * {@link ProxyAuthenticationType#NONE} or {@link ProxyAuthenticationType#USE_DEFAULT_AUTHENTICATOR}.
     * @param password Password used to authenticate with proxy. Optional if {@code authentication} is
     * {@link ProxyAuthenticationType#NONE} or {@link ProxyAuthenticationType#USE_DEFAULT_AUTHENTICATOR}.
     * @param authentication Authentication method to use with proxy.
     *
     * @throws NullPointerException if {@code authentication} is {@code null}.
     * @throws IllegalArgumentException if {@code authentication} is {@link ProxyAuthenticationType#BASIC} or
     * {@link ProxyAuthenticationType#DIGEST} and {@code username} or {@code password} are {@code null}.
     */
    public ProxyConfiguration(String proxyAddress, String username, String password, ProxyAuthenticationType authentication) {
        Objects.requireNonNull(authentication);

        // If the user is authenticating with BASIC or DIGEST, they do not want to use the system-configured
        // authenticator, so we require these values.
        if (authentication == ProxyAuthenticationType.BASIC || authentication == ProxyAuthenticationType.DIGEST) {
            Objects.requireNonNull(username);
            Objects.requireNonNull(password);

            this.credentials = new PasswordAuthentication(username, password.toCharArray());
        } else {
            this.credentials = null;
        }

        this.proxyAddress = proxyAddress;
        this.authentication = authentication;
    }

    String proxyAddress() {
        return proxyAddress;
    }

    PasswordAuthentication credentials() {
        return credentials;
    }

    ProxyAuthenticationType authentication() {
        return authentication;
    }

    /**
     * Gets whether the proxy address has been configured.
     *
     * @return true if the proxy url has been set, and false otherwise.
     */
    public boolean isProxyAddressConfigured() {
        return proxyAddress != null && !proxyAddress.equals("");
    }
}
