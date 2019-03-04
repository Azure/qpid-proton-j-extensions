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

public class ProxyChallengeProcessorImpl implements ProxyChallengeProcessor {

    private final String DIGEST = "digest";
    private final String BASIC = "basic";
    private final String PROXY_AUTH_DIGEST = "Proxy-Authenticate: Digest";
    private final String PROXY_AUTH_BASIC = "Proxy-Authenticate: Basic";
    private final AtomicInteger nonceCounter = new AtomicInteger(0);
    private static Map<String, String> headers;
    private static String host;


    @Override
    public Map<String, String> getHeader(String challengeResp,
                                         String host) {
        this.host = host;

        final Scanner responseScanner = new Scanner(challengeResp);
        final Map<String, String> challengeQuestionValues = new HashMap<String, String>();
        while (responseScanner.hasNextLine()) {
            String line = responseScanner.nextLine();
            if (line.contains(PROXY_AUTH_DIGEST)){
                getChallengeQuestionHeaders(line, challengeQuestionValues);
                computeDigestAuthHeader(challengeQuestionValues, host, getPasswordAuthentication(DIGEST));
                break;
            } else if (line.contains(PROXY_AUTH_BASIC)) {
                computeBasicAuthHeader(getPasswordAuthentication(BASIC));
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

    private PasswordAuthentication getPasswordAuthentication(String scheme) {
        ProxySelector proxySelector = ProxySelector.getDefault();
        URI uri = URI.create(host);
        List<Proxy> proxies = proxySelector.select(uri);

        InetAddress proxyAddr = null;
        Proxy.Type proxyType = null;
        if (isProxyAddressLegal(proxies)) {
            // will be only one element in the proxy list
            proxyAddr = ((InetSocketAddress)proxies.get(0).address()).getAddress();
            proxyType = proxies.get(0).type();
        }
        return Authenticator.requestPasswordAuthentication(
                "",
                proxyAddr,
                0,
                proxyType == null ? "" : proxyType.name(),
                "Event Hubs client websocket proxy support",
                scheme,
                null,
                Authenticator.RequestorType.PROXY);
    }

    private void computeBasicAuthHeader(PasswordAuthentication passwordAuthentication ){
        if (!isPasswordAuthenticationHasValues(passwordAuthentication))
            return;

        String proxyUserName = passwordAuthentication.getUserName();
        String proxyPassword = new String(passwordAuthentication.getPassword());
        final String usernamePasswordPair = proxyUserName + ":" + proxyPassword;
        if (headers == null)
            headers = new HashMap<String, String>();
        headers.put(
                "Proxy-Authorization",
                "Basic " + Base64.getEncoder().encodeToString(usernamePasswordPair.getBytes()));
    }

    private void computeDigestAuthHeader(Map<String, String> challengeQuestionValues,
                                         String uri,
                                         PasswordAuthentication passwordAuthentication) {
        if (!isPasswordAuthenticationHasValues(passwordAuthentication))
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

            if (headers == null) {
                headers = new HashMap<>();
            }
            headers.put("Proxy-Authorization", digestValue);

        } catch(NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }


    private boolean isNullOrEmpty(String string) {
        return (string == null || string.isEmpty());
    }

    private boolean isPasswordAuthenticationHasValues(PasswordAuthentication passwordAuthentication){
        if (passwordAuthentication == null) return false;
        String proxyUserName = passwordAuthentication.getUserName() != null
                ? passwordAuthentication.getUserName()
                : null ;
        String proxyPassword = passwordAuthentication.getPassword() != null
                ? new String(passwordAuthentication.getPassword())
                : null;
        if (isNullOrEmpty(proxyUserName) || isNullOrEmpty(proxyPassword))  return false;
        return true;
    }

    private static boolean isProxyAddressLegal(final List<Proxy> proxies) {
        return proxies != null
                && !proxies.isEmpty()
                && proxies.get(0).address() != null
                && proxies.get(0).address() instanceof InetSocketAddress;
    }
}
