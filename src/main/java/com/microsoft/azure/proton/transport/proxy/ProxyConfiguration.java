// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.PasswordAuthentication;
import java.util.Arrays;
import java.util.Objects;

/**
 * Options for configuring the {@link Proxy}.
 *
 * @see Proxy
 */
public class ProxyConfiguration implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConfiguration.class);

    private final java.net.Proxy proxyAddress;
    private final ProxyAuthenticationType authentication;
    private final ProxyAuthenticator authenticator;
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
        this.authenticator = null;
        this.credentials = null;
        this.proxyAddress = null;
    }

    /**
     * Creates a proxy configuration that uses the {@code proxyAddress} and authenticates with provided {@code
     * username}, {@code password} and {@code authentication}.
     *
     * @param authentication Authentication method to preemptively use with proxy.
     * @param proxyAddress Proxy to use. If {@code null} is passed in, then the system configured {@link
     *         java.net.Proxy} is used.
     * @param username Optional. Username used to authenticate with proxy. If not specified, the system-wide
     *         {@link java.net.Authenticator} is used to fetch credentials.
     * @param password Optional. Password used to authenticate with proxy.
     * @throws NullPointerException if {@code authentication} is {@code null}.
     * @throws IllegalArgumentException if {@code authentication} is {@link ProxyAuthenticationType#BASIC} or
     *         {@link ProxyAuthenticationType#DIGEST} and {@code username} or {@code password} are {@code null}.
     */
    public ProxyConfiguration(ProxyAuthenticationType authentication, java.net.Proxy proxyAddress, String username, String password) {
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
        this.authenticator = null;
    }

    /**
     * Creates a proxy configuration that uses the {@code proxyAddress} and authenticates with provided {@code authenticator}.
     *
     * @param authenticator the proxy authenticator to use.
     * @param proxyAddress Proxy to use.
     * @throws NullPointerException if {@code proxyAddress} or {@code proxyAuthenticator} is {@code null}.
     */
    public ProxyConfiguration(ProxyAuthenticator authenticator, java.net.Proxy proxyAddress) {
        this.authenticator = Objects.requireNonNull(authenticator, "'authenticator' cannot be null.");
        this.proxyAddress = Objects.requireNonNull(proxyAddress, "'proxyAddress' cannot be null.");
        this.authentication = null;
        this.credentials = null;
    }

    /**
     * Gets the proxy address.
     *
     * @return The proxy address. Returns {@code null} if user creates proxy credentials with {@link
     *         ProxyConfiguration#SYSTEM_DEFAULTS}.
     */
    public java.net.Proxy proxyAddress() {
        return proxyAddress;
    }

    /**
     * Gets credentials to authenticate against proxy with.
     *
     * @return The credentials to authenticate against proxy with. Returns {@code null} if no credentials were set. This
     *         occurs when user uses {@link ProxyConfiguration#SYSTEM_DEFAULTS}.
     */
    public PasswordAuthentication credentials() {
        return credentials;
    }

    /**
     * Gets the proxy authentication type to use.
     *
     * @return The proxy authentication type to use. returns {@code null} if no authentication type was set. This occurs
     *         when user uses {@link ProxyConfiguration#SYSTEM_DEFAULTS}.
     */
    public ProxyAuthenticationType authentication() {
        return authentication;
    }

    /**
     * Gets the proxy authenticator to set up the web socket connection to the AMQP broker via a proxy.
     * <p>
     * The authenticator is responsible for selecting one of the authorization schemes that the proxy presents, identify
     * the credentials for the scheme it selects then compute and return the authorization value to be sent through
     * the 'Proxy-Authorization' Header.
     * </p>
     * @return the proxy authenticator.
     */
    public ProxyAuthenticator getAuthenticator() {
        return this.authenticator;
    }

    /**
     * Gets whether the user has defined credentials.
     *
     * @return true if the user has defined the credentials to use, false otherwise.
     */
    public boolean hasUserDefinedCredentials() {
        return credentials != null;
    }

    /**
     * Gets whether the proxy address has been configured. Used to determine whether to use system-defined or
     * user-defined proxy.
     *
     * @return true if the proxy url has been set, and false otherwise.
     */
    public boolean isProxyAddressConfigured() {
        return proxyAddress != null && proxyAddress.address() != null;
    }

    @Override
    public void close() {
        // It is up to us to clear the password field when we are done using it.
        if (credentials != null) {
            Arrays.fill(credentials.getPassword(), '\0');
        }
    }
}
