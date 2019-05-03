package com.microsoft.azure.proton.transport.proxy.impl;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class BasicProxyChallengeProcessorImplTest {
    private static final String headerKey = "Proxy-Authorization";
    private static final String HOSTNAME = "127.0.0.1";
    private static final int PORT = 3128;
    private static final String USERNAME = "basicuser";
    private static final String PASSWORD = "basicpw";
    private Map<String, String> headers = new HashMap<>();

    @Test
    public void testGetHeaderBasic() {
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
        final String host = "";
        final BasicProxyChallengeProcessorImpl proxyChallengeProcessor = new BasicProxyChallengeProcessorImpl(host);
        headers = proxyChallengeProcessor.getHeader();
        Assert.assertEquals("Basic YmFzaWN1c2VyOmJhc2ljcHc=", headers.get(headerKey));
    }
}
