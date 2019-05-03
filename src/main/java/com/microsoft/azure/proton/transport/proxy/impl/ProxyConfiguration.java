package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.ProxyAuthenticationType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.PasswordAuthentication;
import java.util.Objects;

public class ProxyConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConfiguration.class);

    private final String proxyAddress;
    private final ProxyAuthenticationType authentication;
    private final PasswordAuthentication credentials;

    /**
     * Gets the system defaults for proxy configuration and authentication.
     */
    public static final ProxyConfiguration SYSTEM_DEFAULTS = new ProxyConfiguration();

    /**
     * Creates a proxy configuration that uses the system-wide proxy configuration and authenticator.
     */
    private ProxyConfiguration() {
        this.authentication = null;
        this.credentials = null;
        this.proxyAddress = null;
    }

    /**
     * Creates a proxy configuration that uses the {@code proxyAddress} and authenticates with provided
     * {@code username}, {@code password} and {@code authentication}.
     *
     * @param authentication Authentication method to preemptively use with proxy.
     * @param proxyAddress URL of the proxy. If {@code null} is passed in, then the system configured proxy url is used.
     * @param username Optional. Username used to authenticate with proxy. If not specified, the system-wide
     * {@link java.net.Authenticator} is used to fetch credentials.
     * @param password Optional. Password used to authenticate with proxy.
     *
     * @throws NullPointerException if {@code authentication} is {@code null}.
     * @throws IllegalArgumentException if {@code authentication} is {@link ProxyAuthenticationType#BASIC} or
     * {@link ProxyAuthenticationType#DIGEST} and {@code username} or {@code password} are {@code null}.
     */
    public ProxyConfiguration(ProxyAuthenticationType authentication, String proxyAddress, String username, String password) {
        Objects.requireNonNull(authentication);

        this.proxyAddress = proxyAddress;
        this.authentication = authentication;

        if (username != null && password != null) {
            this.credentials = new PasswordAuthentication(username, password.toCharArray());
        } else {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("username or password is null. Using system-wide authentication.");
            }

            this.credentials = null;
        }
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
     * Gets whether the user has defined credentials.
     *
     * @return true if the user has defined the credentials to use, false otherwise.
     */
    boolean hasUserDefinedCredentials() {
        return credentials != null;
    }

    /**
     * Gets whether the proxy address has been configured. Used to determine whether to use system-defined or
     * user-defined proxy.
     *
     * @return true if the proxy url has been set, and false otherwise.
     */
    boolean isProxyAddressConfigured() {
        return !StringUtils.isNullOrEmpty(proxyAddress);
    }
}
