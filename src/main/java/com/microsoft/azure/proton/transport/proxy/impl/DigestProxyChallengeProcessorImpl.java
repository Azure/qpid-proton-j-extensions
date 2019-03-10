package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.ProxyChallengeProcessor;

import javax.xml.bind.DatatypeConverter;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DigestProxyChallengeProcessorImpl implements ProxyChallengeProcessor {

    private final String DIGEST = "digest";
    private final String PROXY_AUTH_DIGEST = "Proxy-Authenticate: Digest";
    private final AtomicInteger nonceCounter = new AtomicInteger(0);
    private final Map<String, String> headers;
    private final ProxyAuthenticator proxyAuthenticator;

    private static String host;
    private static String challenge;

    DigestProxyChallengeProcessorImpl(String host, String challenge) {
        this.host = host;
        this.challenge = challenge;
        headers = new HashMap<>();
        proxyAuthenticator = new ProxyAuthenticator();
    }

    @Override
    public Map<String, String> getHeader() {
        final Scanner responseScanner = new Scanner(challenge);
        final Map<String, String> challengeQuestionValues = new HashMap<>();
        while (responseScanner.hasNextLine()) {
            String line = responseScanner.nextLine();
            if (line.contains(PROXY_AUTH_DIGEST)){
                getChallengeQuestionHeaders(line, challengeQuestionValues);
                computeDigestAuthHeader(challengeQuestionValues, host, proxyAuthenticator.getPasswordAuthentication(DIGEST, host));
                break;
            }
        }
        return headers;
    }

    private void getChallengeQuestionHeaders(String line, Map<String, String> challengeQuestionValues) {
        String context =  line.substring(PROXY_AUTH_DIGEST.length());
        String[] headerValues = context.split(",");

        for (String headerValue : headerValues) {
            if (headerValue.contains("=")) {
                String key = headerValue.substring(0, headerValue.indexOf("="));
                String value = headerValue.substring(headerValue.indexOf("=") + 1);
                challengeQuestionValues.put(key.trim(), value.replaceAll("\"", "").trim());
            }
        }
    }

    private void computeDigestAuthHeader(Map<String, String> challengeQuestionValues,
                                         String uri,
                                         PasswordAuthentication passwordAuthentication) {
        if (!proxyAuthenticator.isPasswordAuthenticationHasValues(passwordAuthentication))
            return;

        String proxyUserName = passwordAuthentication.getUserName();
        String proxyPassword = new String(passwordAuthentication.getPassword());
        String digestValue;
        try {
            String nonce = challengeQuestionValues.get("nonce");
            String realm = challengeQuestionValues.get("realm");
            String qop = challengeQuestionValues.get("qop");

            MessageDigest md5 = MessageDigest.getInstance("md5");
            SecureRandom secureRandom = new SecureRandom();
            String a1 = DatatypeConverter.printHexBinary(md5.digest(String.format("%s:%s:%s", proxyUserName, realm, proxyPassword).getBytes("UTF-8"))).toLowerCase();
            String a2 = DatatypeConverter.printHexBinary(md5.digest(String.format("%s:%s", "CONNECT", uri).getBytes("UTF-8"))).toLowerCase();

            byte[] cnonceBytes = new byte[16];
            secureRandom.nextBytes(cnonceBytes);
            String cnonce = DatatypeConverter.printHexBinary(cnonceBytes).toLowerCase();
            String response;
            if (qop == null || qop.isEmpty()) {
                response = DatatypeConverter.printHexBinary(md5.digest(String.format("%s:%s:%s", a1, nonce, a2).getBytes("UTF-8"))).toLowerCase();
                digestValue = String.format("Digest username=\"%s\",realm=\"%s\",nonce=\"%s\",uri=\"%s\",cnonce=\"%s\",response=\"%s\"",
                        proxyUserName, realm, nonce, uri, cnonce, response);
            } else {
                int nc = nonceCounter.incrementAndGet();
                response = DatatypeConverter.printHexBinary(md5.digest(String.format("%s:%s:%08X:%s:%s:%s", a1, nonce, nc, cnonce, qop, a2).getBytes("UTF-8"))).toLowerCase();
                digestValue = String.format("Digest username=\"%s\",realm=\"%s\",nonce=\"%s\",uri=\"%s\",cnonce=\"%s\",nc=%08X,response=\"%s\",qop=\"%s\"",
                        proxyUserName, realm, nonce, uri, cnonce, nc, response, qop);
            }

            headers.put("Proxy-Authorization", digestValue);
        } catch(NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }
}
