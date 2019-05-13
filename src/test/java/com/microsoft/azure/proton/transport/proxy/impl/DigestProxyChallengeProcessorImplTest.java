package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.ProxyAuthenticationType;
import com.microsoft.azure.proton.transport.proxy.ProxyConfiguration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static com.microsoft.azure.proton.transport.proxy.impl.DigestProxyChallengeProcessorImpl.printHexBinary;
import static java.nio.charset.StandardCharsets.UTF_8;

public class DigestProxyChallengeProcessorImplTest {
    private static final String NEW_LINE = "\r\n";
    private static final String HOSTNAME = "127.0.0.1";
    private static final int PORT = 3128;
    private static final String USERNAME = "my-username";
    private static final String PASSWORD = "my-password";
    private static MessageDigest md5;

    private ProxySelector originalProxy;

    @BeforeClass
    public static void init() throws NoSuchAlgorithmException {
        md5 = MessageDigest.getInstance(DigestProxyChallengeProcessorImpl.DEFAULT_ALGORITHM);
    }

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
        // Arrange
        final String realm = "Squid proxy-caching web server";
        final String nonce = "zWV5XAAAAAAgz1ACAAAAAE9hOwIAAAAA";
        final String qop = "auth";

        // a1 and a2 are hash values generated as an intermediate step in the Digest algorithm.
        final String a1 = "6ea8f6c05777aae5987035155da478bc";
        final String a2 = "9b1f7b831464108253ea5a0fd0ebb3d9";

        final String response = generateProxyChallenge(realm, nonce, qop);
        final DigestValidator validator = new DigestValidator(USERNAME, realm, nonce, "00000001", HOSTNAME, qop);

        // Act
        final DigestProxyChallengeProcessorImpl proxyChallengeProcessor =
                new DigestProxyChallengeProcessorImpl(HOSTNAME, response, new ProxyAuthenticator());
        Map<String, String> headers = proxyChallengeProcessor.getHeader();

        // Assert
        String resp = headers.get(Constants.PROXY_AUTHORIZATION);
        validator.assertEquals(resp, a1, a2);
    }

    /**
     * Verify that when we explicitly pass in proxy configuration, that host and those credentials are used rather than
     * the system defined ones.
     */
    @Test
    public void testGetHeaderDigestWithProxy() {
        // Arrange
        final String realm = "My Test Realm";
        final String nonce = "A randomly generated nonce";
        final String qop = "auth";
        final String challenge = generateProxyChallenge(realm, nonce, qop);

        final String host = "foobar.myhost.com";
        final InetSocketAddress address = InetSocketAddress.createUnresolved(host, 3138);
        final Proxy proxy = new Proxy(Proxy.Type.SOCKS, address);
        final String username = "my-different-username";
        final String password = "my-different-password";
        final ProxyConfiguration configuration = new ProxyConfiguration(ProxyAuthenticationType.DIGEST, proxy, username, password);
        final ProxyAuthenticator authenticator = new ProxyAuthenticator(configuration);

        // a1 and a2 are hash values generated as an intermediate step in the Digest algorithm.
        final String a1 = "d9365dc421b15c1fe23ac413788839c7";
        final String a2 = "5c8c78994a9672c0b394d4615f3f8c23";
        final DigestValidator validator = new DigestValidator(username, realm, nonce, "00000001", host, qop);

        // Act
        final DigestProxyChallengeProcessorImpl proxyChallengeProcessor =
                new DigestProxyChallengeProcessorImpl(host, challenge, authenticator);
        Map<String, String> headers = proxyChallengeProcessor.getHeader();

        // Assert
        String resp = headers.get(Constants.PROXY_AUTHORIZATION);
        validator.assertEquals(resp, a1, a2);
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

        // Arrange
        final String realm = "My Test Realm";
        final String nonce = "A randomly generated nonce";
        final String qop = "auth";
        final String challenge = generateProxyChallenge(realm, nonce, qop);

        final String host = "foobar.myhost.com";
        final InetSocketAddress address = InetSocketAddress.createUnresolved(host, 3138);
        final Proxy proxy = new Proxy(Proxy.Type.SOCKS, address);
        final ProxyConfiguration configuration = new ProxyConfiguration(ProxyAuthenticationType.DIGEST, proxy, null, null);
        final ProxyAuthenticator authenticator = new ProxyAuthenticator(configuration);
        final DigestProxyChallengeProcessorImpl proxyChallengeProcessor =
                new DigestProxyChallengeProcessorImpl(host, challenge, authenticator);

        // Act
        Map<String, String> headers = proxyChallengeProcessor.getHeader();

        // Assert
        Assert.assertTrue(headers.isEmpty());
    }

    private static String generateProxyChallenge(String realm, String nonce, String qop) {
        final String digest = String.format("%s %s realm=\"%s\", nonce=\"%s\", qop=\"%s\", stale=false",
                Constants.PROXY_AUTHENTICATE_HEADER, Constants.DIGEST, realm, nonce, qop);
        final String basic = String.format("%s %s realm=\"%s\"",
                Constants.PROXY_AUTHENTICATE_HEADER, Constants.BASIC, realm);

        return String.join(NEW_LINE,
                "HTTP/1.1 407 Proxy Authentication Required",
                "X-Squid-Error: ERR_CACHE_ACCESS_DENIED 0",
                digest,
                basic)
                + NEW_LINE;
    }

    private class DigestValidator {
        private static final String USERNAME_KEY = "username";
        private static final String NONCE_KEY = "nonce";
        private static final String URI_KEY = "uri";
        private static final String CNONCE_KEY = "cnonce";
        private static final String RESPONSE_KEY = "response";
        private static final String NC_KEY = "nc";
        private static final String QOP_KEY = "qop";

        private final Map<String, String> expected = new HashMap<>();

        DigestValidator(String username, String realm, String nonce, String nc, String uri, String qop) {
            expected.put(NC_KEY, nc);
            expected.put(USERNAME_KEY, username);
            expected.put("realm", realm);
            expected.put(NONCE_KEY, nonce);
            expected.put(URI_KEY, uri);
            expected.put(QOP_KEY, qop);
            expected.put(CNONCE_KEY, null);
            expected.put(RESPONSE_KEY, null);
        }

        void assertEquals(String response, String a1, String a2) {
            Assert.assertNotNull(response);
            Assert.assertTrue(response.startsWith(Constants.DIGEST));

            String[] split = response.substring(Constants.DIGEST.length()).split(",");

            Map<String, String> actual = Arrays.stream(split).map(p -> {
                String[] part = p.split("=");
                Assert.assertEquals(2, part.length);

                String key = part[0].trim();
                String value = part[1].replace("\"", "").trim();
                return new Pair(key, value);
            }).collect(Collectors.toMap(Pair::key, Pair::value));

            Assert.assertEquals(expected.size(), actual.size());

            expected.forEach((key, value) -> {
                // Skipping "cnonce" and "response" because they are randomly generated each time. Later on, we'll
                // check for the response's validity.
                if (key.equals(CNONCE_KEY) || key.equals(RESPONSE_KEY)) {
                    return;
                }

                Assert.assertTrue(actual.containsKey(key));
                Assert.assertEquals(value, actual.get(key));
            });

            String expectedRawResponse = String.join(":",
                    a1, expected.get(NONCE_KEY), expected.get(NC_KEY), actual.get(CNONCE_KEY), expected.get(QOP_KEY), a2);
            String expectedResponse = printHexBinary(md5.digest(expectedRawResponse.getBytes(UTF_8))).toLowerCase(Locale.ROOT);

            Assert.assertEquals(expectedResponse, actual.get(RESPONSE_KEY));
        }
    }

    private static class Pair {
        private final String key;
        private final String value;

        Pair(String key, String value) {
            this.key = key;
            this.value = value;
        }

        String key() {
            return this.key;
        }

        String value() {
            return this.value;
        }
    }
}
