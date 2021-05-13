// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.ProxyAuthenticationType;
import com.microsoft.azure.proton.transport.proxy.ProxyConfiguration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ProxyAuthenticatorTests {
    private static final String USERNAME = "test-user";
    private static final String PASSWORD = "test-password!";
    private static final char[] PASSWORD_CHAR_ARRAY = PASSWORD.toCharArray();
    private static final String PROXY_ADDRESS = "foo.proxy.com";

    private ProxySelector originalProxySelector;
    private ProxySelector proxySelector;
    private TestAuthenticator authenticator;

    /**
     * Creates mocks of the proxy selector and authenticator and sets them as defaults.
     */
    @Before
    public void setup() {
        originalProxySelector = ProxySelector.getDefault();

        authenticator = new TestAuthenticator(USERNAME, PASSWORD);
        proxySelector = mock(ProxySelector.class, Mockito.CALLS_REAL_METHODS);

        ProxySelector.setDefault(proxySelector);
        Authenticator.setDefault(authenticator);
    }

    @After
    public void teardown() {
        ProxySelector.setDefault(originalProxySelector);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrows() {
        new ProxyAuthenticator(null);
    }

    @Test
    public void useSystemDefaults() {
        // Arrange
        final String scheme = "Digest";
        final InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        final InetSocketAddress socketAddress = new InetSocketAddress(loopbackAddress, 443);
        final ProxyAuthenticator proxyAuthenticator = new ProxyAuthenticator();

        final Proxy proxy = new Proxy(Proxy.Type.HTTP, socketAddress);
        final List<Proxy> proxies = new ArrayList<>();
        proxies.add(proxy);

        when(proxySelector.select(argThat(u -> u != null && u.getPath().equals(PROXY_ADDRESS))))
                .thenReturn(proxies);

        // Act
        PasswordAuthentication authentication = proxyAuthenticator.getPasswordAuthentication(scheme, PROXY_ADDRESS);

        // Assert
        Assert.assertNotNull(authentication);
        Assert.assertEquals(USERNAME, authentication.getUserName());
        Assert.assertArrayEquals(PASSWORD_CHAR_ARRAY, authentication.getPassword());

        Assert.assertEquals(Proxy.Type.HTTP.name(), authenticator.requestingProtocol());
        Assert.assertEquals(loopbackAddress, authenticator.requestingSite());
        Assert.assertEquals(scheme, authenticator.requestingScheme());
        Assert.assertEquals(Authenticator.RequestorType.PROXY, authenticator.requestorType());
    }

    /**
     * Verifies that if user specifies the credentials that is fully populated, use the credentials. Regardless of the
     * proxy address in {@link ProxyAuthenticator#getPasswordAuthentication(String, String)}.
     */
    @Test
    public void useProxyConfigurationWithCredentials() {
        // Arrange
        final String scheme = "Digest";
        final String host = "foobar.myhost.com";
        final InetSocketAddress address = InetSocketAddress.createUnresolved(host, 3138);
        final Proxy proxy = new Proxy(Proxy.Type.SOCKS, address);

        final ProxyConfiguration configuration = new ProxyConfiguration(ProxyAuthenticationType.BASIC,
                proxy, "my-username", "my-password");
        final ProxyAuthenticator proxyAuthenticator = new ProxyAuthenticator(configuration);

        final List<Proxy> proxies = new ArrayList<>();
        proxies.add(proxy);

        when(proxySelector.select(argThat(u -> u != null && u.getPath().equals(PROXY_ADDRESS))))
                .thenReturn(proxies);

        // Act
        PasswordAuthentication authentication = proxyAuthenticator.getPasswordAuthentication(scheme, PROXY_ADDRESS);

        // Assert
        verifyZeroInteractions(proxySelector);

        Assert.assertNotNull(authentication);
        Assert.assertEquals(configuration.credentials().getUserName(), authentication.getUserName());
        Assert.assertArrayEquals(configuration.credentials().getPassword(), authentication.getPassword());

        // We never use the system-wide authenticator. Verify that.
        Assert.assertNull(authenticator.requestingScheme());
    }

    /**
     * Verifies that if user specifies the credentials but no proxy address, we use the credentials.
     */
    @Test
    public void useProxyConfigurationWithCredentialsNoAddress() {
        // Arrange
        final String scheme = "Digest";
        final String host = "foobar.myhost.com";
        final InetSocketAddress address = InetSocketAddress.createUnresolved(host, 3138);
        final Proxy proxy = new Proxy(Proxy.Type.SOCKS, address);
        final ProxyConfiguration configuration = new ProxyConfiguration(ProxyAuthenticationType.BASIC,
                null, "my-username", "my-password");
        final ProxyAuthenticator proxyAuthenticator = new ProxyAuthenticator(configuration);

        final List<Proxy> proxies = new ArrayList<>();
        proxies.add(proxy);

        when(proxySelector.select(argThat(u -> u != null && u.getPath().equals(PROXY_ADDRESS))))
                .thenReturn(proxies);

        // Act
        PasswordAuthentication authentication = proxyAuthenticator.getPasswordAuthentication(scheme, PROXY_ADDRESS);

        // Assert
        verifyZeroInteractions(proxySelector);

        Assert.assertNotNull(authentication);
        Assert.assertEquals(configuration.credentials().getUserName(), authentication.getUserName());
        Assert.assertArrayEquals(configuration.credentials().getPassword(), authentication.getPassword());

        // We never use the system-wide authenticator. Verify that.
        Assert.assertNull(authenticator.requestingScheme());
    }

    /**
     * Verifies that if user specifies the proxy address but not the credentials, use the system-wide authenticator to
     * figure out the credentials for that proxy address.
     */
    @Test
    public void useProxyAddressWithSystemAuthentication() {
        // Arrange
        final String scheme = "Digest";
        final String host = "my-proxy.myhost.com";
        final InetSocketAddress address = InetSocketAddress.createUnresolved(host, 3138);
        final Proxy proxy = new Proxy(Proxy.Type.SOCKS, address);
        final ProxyConfiguration configuration = new ProxyConfiguration(ProxyAuthenticationType.BASIC, proxy,
                null, null);
        final ProxyAuthenticator proxyAuthenticator = new ProxyAuthenticator(configuration);

        final List<Proxy> proxies = new ArrayList<>();
        proxies.add(proxy);

        when(proxySelector.select(argThat(u -> u != null && u.getPath().equals(PROXY_ADDRESS))))
                .thenReturn(proxies);

        // Act
        PasswordAuthentication authentication = proxyAuthenticator.getPasswordAuthentication(scheme, PROXY_ADDRESS);

        // Assert
        Assert.assertNotNull(authentication);
        Assert.assertEquals(USERNAME, authentication.getUserName());
        Assert.assertArrayEquals(PASSWORD_CHAR_ARRAY, authentication.getPassword());

        Assert.assertEquals(host, authenticator.requestingHost());
        Assert.assertNull(authenticator.requestingProtocol());
        Assert.assertNull(authenticator.requestingSite());
        Assert.assertEquals(scheme, authenticator.requestingScheme());
        Assert.assertEquals(Authenticator.RequestorType.PROXY, authenticator.requestorType());
    }
}
