package com.microsoft.azure.proton.transport.proxy.impl;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class DigestProxyChallengeProcessorImplTest {

    private final String headerKey = "Proxy-Authorization";
    private Map<String, String> headers = new HashMap<>();
    private final String HOSTNAME = "127.0.0.1";
    private final int PORT = 3128;
    private final String USERNAME = "username";
    private final String PASSWORD = "password";

    @Test
    public void testGetHeaderDigest() {
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                List<Proxy> proxies = new ArrayList<>();
                proxies.add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(HOSTNAME, PORT)));
                return proxies;
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                System.out.println("PROXY CONNECTION FAILED:  " + uri.toString());
                System.out.println("PROXY CONNECTION FAILED: " + sa.toString());
                System.out.println("PROXY CONNECTION FAILED: " + ioe.toString());
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

        final String response = "HTTP/1.1 407 Proxy Authentication Required\r\n" +
            "X-Squid-Error: ERR_CACHE_ACCESS_DENIED 0\r\n" +
            "Proxy-Authenticate: Digest realm=\"Squid proxy-caching web server\", nonce=\"zWV5XAAAAAAgz1ACAAAAAE9hOwIAAAAA\", qop=\"auth\", stale=false\r\n" +
            "Proxy-Authenticate: Basic realm=\"Squid proxy-caching web server\"\r\n" ;

        final DigestProxyChallengeProcessorImpl proxyChallengeProcessor = new DigestProxyChallengeProcessorImpl("", response);
        headers = proxyChallengeProcessor.getHeader();
        String resp = headers.get(headerKey);
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
