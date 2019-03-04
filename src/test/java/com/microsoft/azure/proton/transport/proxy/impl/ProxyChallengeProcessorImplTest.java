//package com.microsoft.azure.proton.transport.proxy.impl;
//
//import com.microsoft.azure.proton.transport.proxy.ProxyChallengeProcessor;
//import org.junit.Assert;
//import org.junit.Test;
//
//import java.util.HashMap;
//import java.util.Map;
//
//public class ProxyChallengeProcessorImplTest {
//
//    private String headerKey = "Proxy-Authorization";
//    private String headerValue = "Basic";
//    private Map<String, String> headers = new HashMap<>();
//
//    @Test
//    public void testGetHeaderBasic() {
//        final String response = "HTTP/1.1 407 Proxy Authentication Required\r\n" +
//            "X-Squid-Error: ERR_CACHE_ACCESS_DENIED 0\r\n" +
//            "Proxy-Authenticate: Basic realm=\"Squid proxy-caching web server\"\r\n" +
//            "Proxy-Authenticate: Digest realm=\"Squid proxy-caching web server\", nonce=\"zWV5XAAAAAAgz1ACAAAAAE9hOwIAAAAA\", qop=\"auth\", stale=false\r\n";
//        final String host = "";
//        final ProxyChallengeProcessorImpl proxyChallengeProcessor = new ProxyChallengeProcessorImpl();
//        headers = proxyChallengeProcessor.getHeader(response, host);
//        Assert.assertTrue(headers.get(headerKey).substring(0, headerValue.length()).equals(headerValue));
//    }
//
//    @Test
//    public void testGetHeaderDigest() {
//        final String response = "HTTP/1.1 407 Proxy Authentication Required\r\n" +
//            "X-Squid-Error: ERR_CACHE_ACCESS_DENIED 0\r\n" +
//            "Proxy-Authenticate: Digest realm=\"Squid proxy-caching web server\", nonce=\"zWV5XAAAAAAgz1ACAAAAAE9hOwIAAAAA\", qop=\"auth\", stale=false\r\n" +
//            "Proxy-Authenticate: Basic realm=\"Squid proxy-caching web server\"\r\n" ;
//        final String host = "";
//        final ProxyChallengeProcessorImpl proxyChallengeProcessor = new ProxyChallengeProcessorImpl();
//        headers = proxyChallengeProcessor.getHeader(response, host);
//        Assert.assertTrue(headers.get(headerKey).substring(0, headerValue.length()).equals(headerValue));
//
//    }
//
//
//
//}
