package com.microsoft.azure.proton.transport.proxy.impl;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(value = Theories.class)
public class ProxyConfigurationTest {
    private static final String USERNAME = "test-user";
    private static final String PASSWORD = "test-password!";
    private static final char[] PASSWORD_CHARS = PASSWORD.toCharArray();
    private static final String PROXY_ADDRESS = "foo.proxy.com";
    private static final ProxyAuthenticationType AUTHENTICATION_TYPE = ProxyAuthenticationType.BASIC;

    @DataPoints("userConfigurations")
    public static ProxyConfiguration[] userConfigurations() {
        return new ProxyConfiguration[]{
                new ProxyConfiguration(AUTHENTICATION_TYPE, PROXY_ADDRESS, null, PASSWORD),
                new ProxyConfiguration(AUTHENTICATION_TYPE, PROXY_ADDRESS, USERNAME, null),
                new ProxyConfiguration(AUTHENTICATION_TYPE, PROXY_ADDRESS, null, null),
        };
    }

    @Test
    public void systemConfiguredConfiguration() {
        ProxyConfiguration configuration = ProxyConfiguration.SYSTEM_DEFAULTS;

        Assert.assertFalse(configuration.isProxyAddressConfigured());
        Assert.assertFalse(configuration.hasUserDefinedCredentials());

        Assert.assertNull(configuration.proxyAddress());
        Assert.assertNull(configuration.credentials());
        Assert.assertNull(configuration.authentication());
    }

    @Test
    public void userDefinedConfiguration() {
        ProxyConfiguration configuration = new ProxyConfiguration(AUTHENTICATION_TYPE, PROXY_ADDRESS, USERNAME, PASSWORD);

        Assert.assertTrue(configuration.isProxyAddressConfigured());
        Assert.assertTrue(configuration.hasUserDefinedCredentials());

        Assert.assertEquals(AUTHENTICATION_TYPE, configuration.authentication());
        Assert.assertEquals(PROXY_ADDRESS, configuration.proxyAddress());
        Assert.assertEquals(USERNAME, configuration.credentials().getUserName());
        Assert.assertArrayEquals(PASSWORD_CHARS, configuration.credentials().getPassword());
    }

    /**
     * Verify that if the user has not provided a username or password, we cannot construct valid credentials from that.
     */
    @Theory
    public void userDefinedConfigurationMissingData(@FromDataPoints("userConfigurations") ProxyConfiguration configuration) {
        Assert.assertTrue(configuration.isProxyAddressConfigured());
        Assert.assertFalse(configuration.hasUserDefinedCredentials());

        Assert.assertNull(configuration.credentials());

        Assert.assertEquals(AUTHENTICATION_TYPE, configuration.authentication());
        Assert.assertEquals(PROXY_ADDRESS, configuration.proxyAddress());
    }

    /**
     * Verify that if the user has not provided a proxy address, we will use the system-wide configured proxy.
     */
    @Test
    public void userDefinedConfigurationNoProxyAddress() {
        ProxyAuthenticationType type = ProxyAuthenticationType.DIGEST;
        ProxyConfiguration configuration = new ProxyConfiguration(type, null, USERNAME, PASSWORD);

        Assert.assertFalse(configuration.isProxyAddressConfigured());
        Assert.assertTrue(configuration.hasUserDefinedCredentials());

        Assert.assertEquals(type, configuration.authentication());
        Assert.assertNotNull(configuration.credentials());

        Assert.assertEquals(USERNAME, configuration.credentials().getUserName());
        Assert.assertArrayEquals(PASSWORD_CHARS, configuration.credentials().getPassword());
    }
}
