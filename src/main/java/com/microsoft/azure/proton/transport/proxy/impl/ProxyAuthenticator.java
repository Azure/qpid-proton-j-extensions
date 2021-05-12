package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.ProxyConfiguration;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Responds to proxy challenge requests by providing authentication information.
 */
class ProxyAuthenticator {
    private static final String PROMPT = "Event Hubs client web socket proxy support";

    private final ProxyConfiguration configuration;

    /**
     * Creates an authenticator that authenticates using system-configured authenticator and system-configured proxy
     * settings.
     */
    ProxyAuthenticator() {
        this(ProxyConfiguration.SYSTEM_DEFAULTS);
    }

    /**
     * Creates an authenticator that responses to authentication requests with the provided configuration.
     *
     * @param configuration Proxy configuration to use for requests.
     * @throws NullPointerException if {@code configuration} is {@code null}.
     */
    ProxyAuthenticator(ProxyConfiguration configuration) {
        Objects.requireNonNull(configuration);

        this.configuration = configuration;
    }

    /**
     * Gets the credentials to use for proxy authentication given the {@code scheme} and {@code host}. Finds credentials
     * to return in the following order
     * <ol>
     *     <li>If user specified username/password from {@link ProxyConfiguration}, return that.</li>
     *     <li>If user specified proxy address, tries to fetch credentials using the system-wide authenticator.</li>
     *     <li>Use system-wide proxy configuration and authenticator to fetch credentials.</li>
     * </ol>
     *
     * @param scheme The authentication scheme for the proxy.
     * @param host The proxy's URL that is requesting authentication.
     * @return The username and password to authenticate against proxy with.
     */
    PasswordAuthentication getPasswordAuthentication(String scheme, String host) {
        if (configuration.hasUserDefinedCredentials()) {
            return configuration.credentials();
        }

        // The user has specified the proxy address, so we'll use that address to try to fetch the system-wide
        // credentials for this.
        if (configuration.isProxyAddressConfigured()) {
            // We can cast this because Proxy ctor verifies that address is an instance of InetSocketAddress.
            InetSocketAddress address = (InetSocketAddress) configuration.proxyAddress().address();
            return Authenticator.requestPasswordAuthentication(
                    address.getHostName(),
                    address.getAddress(),
                    0,
                    null,
                    PROMPT,
                    scheme,
                    null,
                    Authenticator.RequestorType.PROXY);
        }

        // Otherwise, use the system-configured proxies and authenticator to fetch the credentials.
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
            proxyAddr = ((InetSocketAddress) proxies.get(0).address()).getAddress();
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
