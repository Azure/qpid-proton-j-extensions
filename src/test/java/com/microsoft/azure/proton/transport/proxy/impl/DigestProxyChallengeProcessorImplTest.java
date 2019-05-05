package com.microsoft.azure.proton.transport.proxy.impl;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class DigestProxyChallengeProcessorImplTest {
    private final String HOSTNAME = "127.0.0.1";
    private final int PORT = 3128;
    private final String USERNAME = "username";
    private final String PASSWORD = "password";
    private ProxySelector originalProxy;

    @Before
    public void setup() {
        originalProxy = ProxySelector.getDefault();

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

    @After
    public void teardown() {
        ProxySelector.setDefault(originalProxy);
    }

    @Test
    public void testGetHeaderDigest() {
        final String response = "HTTP/1.1 407 Proxy Authentication Required\r\n" +
            "X-Squid-Error: ERR_CACHE_ACCESS_DENIED 0\r\n" +
            "Proxy-Authenticate: Digest realm=\"Squid proxy-caching web server\", nonce=\"zWV5XAAAAAAgz1ACAAAAAE9hOwIAAAAA\", qop=\"auth\", stale=false\r\n" +
            "Proxy-Authenticate: Basic realm=\"Squid proxy-caching web server\"\r\n" ;

        final DigestProxyChallengeProcessorImpl proxyChallengeProcessor = new DigestProxyChallengeProcessorImpl("", response, new ProxyAuthenticator());
        Map<String, String> headers = proxyChallengeProcessor.getHeader();
        String resp = headers.get(Constants.PROXY_AUTHORIZATION);
        Assert.assertTrue(resp.contains("Digest "));
        Assert.assertTrue(resp.contains("username=\""));
        Assert.assertTrue(resp.contains("realm=\""));
        Assert.assertTrue(resp.contains("nonce=\""));
        Assert.assertTrue(resp.contains("uri=\""));
        Assert.assertTrue(resp.contains("cnonce=\""));
        Assert.assertTrue(resp.contains("nc="));
        Assert.assertTrue(resp.contains("response=\""));
        Assert.assertTrue(resp.contains("qop=\""));
    }
}
