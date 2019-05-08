package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.ProxyAuthenticationType;
import com.microsoft.azure.proton.transport.proxy.ProxyConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BasicProxyChallengeProcessorImplTest {
    private static final String HOSTNAME = "127.0.0.1";
    private static final int PORT = 3128;
    private static final String USERNAME = "basicuser";
    private static final String PASSWORD = "basicpw";

    @Before
    public void setup() {
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                List<Proxy> proxies = new ArrayList<>();
                proxies.add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(HOSTNAME, PORT)));
                return proxies;
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                System.out.format("PROXY CONNECTION FAILED: URI = %s, Socket Address = %s, IO Exception = %s\n", uri.toString(), sa.toString(), ioe.toString());
            }
        });

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == Authenticator.RequestorType.PROXY)
                    return new PasswordAuthentication(USERNAME, PASSWORD.toCharArray());
                return super.getPasswordAuthentication();
            }
        });
    }

    @Test
    public void testGetHeaderBasic() {
        final String host = "";
        final String expected = String.join(" ", Constants.BASIC, "YmFzaWN1c2VyOmJhc2ljcHc=");
        final ProxyAuthenticator authenticator = new ProxyAuthenticator();
        final BasicProxyChallengeProcessorImpl proxyChallengeProcessor = new BasicProxyChallengeProcessorImpl(host, authenticator);

        Map<String, String> headers = proxyChallengeProcessor.getHeader();
        Assert.assertEquals(expected, headers.get(Constants.PROXY_AUTHORIZATION));
    }

    /**
     * Verifies that if user provides their own credentials, it will be used.
     */
    @Test
    public void testWithProxyConfiguration() {
        final InetSocketAddress address = InetSocketAddress.createUnresolved("foo.proxy.com", 3138);
        final Proxy proxy = new Proxy(Proxy.Type.SOCKS, address);
        final String username = "test-username";
        final String password = "test-password!!";

        final byte[] encoded = String.join(":", username, password).getBytes(StandardCharsets.UTF_8);
        final String base64Encoded = Base64.getEncoder().encodeToString(encoded);
        final String expectedAuthValue = String.join(" ", Constants.BASIC, base64Encoded);

        final ProxyConfiguration configuration = new ProxyConfiguration(ProxyAuthenticationType.DIGEST, proxy, username, password );
        final ProxyAuthenticator authenticator = new ProxyAuthenticator(configuration);
        final BasicProxyChallengeProcessorImpl proxyChallengeProcessor = new BasicProxyChallengeProcessorImpl("something.com", authenticator);

        Map<String, String> headers = proxyChallengeProcessor.getHeader();
        Assert.assertEquals(expectedAuthValue, headers.get(Constants.PROXY_AUTHORIZATION));
    }

    /**
     * Verifies that if we cannot obtain credentials from proxyAuthenticator, then we return null.
     */
    @Test
    public void cannotObtainPasswordCredentialsWithValues() {
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == Authenticator.RequestorType.PROXY)
                    return null;

                Assert.fail("Should always be type of ProxyRequest.");
                throw new RuntimeException("Should be of type ProxyRequest");
            }
        });

        final InetSocketAddress address = InetSocketAddress.createUnresolved("foo.proxy.com", 3138);
        final Proxy proxy = new Proxy(Proxy.Type.SOCKS, address);
        final ProxyConfiguration configuration = new ProxyConfiguration(ProxyAuthenticationType.BASIC, proxy, null, null);
        final ProxyAuthenticator authenticator = new ProxyAuthenticator(configuration);
        final BasicProxyChallengeProcessorImpl proxyChallengeProcessor = new BasicProxyChallengeProcessorImpl("something.foo.com", authenticator);

        Map<String, String> headers = proxyChallengeProcessor.getHeader();

        Assert.assertNull(headers);
    }
}
