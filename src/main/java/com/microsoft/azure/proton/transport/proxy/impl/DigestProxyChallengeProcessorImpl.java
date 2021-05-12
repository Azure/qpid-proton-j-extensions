// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.ProxyChallengeProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.PasswordAuthentication;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Implementation to support digest authentication for proxies.
 *
 * @see <a href="https://developer.mozilla.org/docs/Web/HTTP/Headers/Proxy-Authenticate">Proxy-Authenticate</a>
 * @see <a href="https://developer.mozilla.orgdocs/Web/HTTP/Authentication#authentication_schemes">Authentication Schemes</a>
 */
public class DigestProxyChallengeProcessorImpl implements ProxyChallengeProcessor {
    static final String DEFAULT_ALGORITHM = "MD5";
    private static final String PROXY_AUTH_DIGEST = Constants.PROXY_AUTHENTICATE_HEADER + " " + Constants.DIGEST;
    private static final char[] HEX_CODE = "0123456789ABCDEF".toCharArray();

    private final Logger logger = LoggerFactory.getLogger(DigestProxyChallengeProcessorImpl.class);
    private final AtomicInteger nonceCounter = new AtomicInteger(0);
    private final Map<String, String> headers;
    private final ProxyAuthenticator proxyAuthenticator;

    private final String host;
    private final String challenge;

    DigestProxyChallengeProcessorImpl(String host, String challenge, ProxyAuthenticator authenticator) {
        Objects.requireNonNull(authenticator);
        this.host = host;
        this.challenge = challenge;
        headers = new HashMap<>();
        proxyAuthenticator = authenticator;
    }

    @Override
    public Map<String, String> getHeader() {
        final Scanner responseScanner = new Scanner(challenge);
        final Map<String, String> challengeQuestionValues = new HashMap<>();

        if (logger.isInfoEnabled()) {
            logger.info("Fetching header from:");
        }

        while (responseScanner.hasNextLine()) {
            String line = responseScanner.nextLine();

            if (logger.isInfoEnabled()) {
                logger.info(line);
            }

            if (line.contains(PROXY_AUTH_DIGEST)) {
                getChallengeQuestionHeaders(line, challengeQuestionValues);
                computeDigestAuthHeader(challengeQuestionValues, host,
                        proxyAuthenticator.getPasswordAuthentication(Constants.DIGEST_LOWERCASE, host));

                logger.info("Finished getting auth header.");
                break;
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("Headers added are:");

            headers.forEach((key, value) -> {
                logger.info("{}: {}", key, value);
            });
        }

        return headers;
    }

    private void getChallengeQuestionHeaders(String line, Map<String, String> challengeQuestionValues) {
        final String context = line.substring(PROXY_AUTH_DIGEST.length());
        final String[] headerValues = context.split(",");

        if (logger.isInfoEnabled()) {
            logger.info("Fetching challenge questions.");
        }

        for (String headerValue : headerValues) {
            if (headerValue.contains("=")) {
                String key = headerValue.substring(0, headerValue.indexOf("="));
                String value = headerValue.substring(headerValue.indexOf("=") + 1);
                challengeQuestionValues.put(key.trim(), value.replaceAll("\"", "").trim());
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("Challenge questions are: ");

            challengeQuestionValues.forEach((key, value) -> {
                logger.info("{}: {}", key, value);
            });
        }
    }

    private void computeDigestAuthHeader(Map<String, String> challengeQuestionValues,
                                         String uri,
                                         PasswordAuthentication passwordAuthentication) {
        if (logger.isInfoEnabled()) {
            logger.info("Computing password authentication...");
        }

        if (!ProxyAuthenticator.isPasswordAuthenticationHasValues(passwordAuthentication)) {
            if (logger.isErrorEnabled()) {
                logger.error("Password authentication does not have values. Not computing authorization header.");
            }

            return;
        }

        final String proxyUserName = passwordAuthentication.getUserName();
        final String proxyPassword = new String(passwordAuthentication.getPassword());

        try {
            String digestValue;
            final String nonce = challengeQuestionValues.get("nonce");
            final String realm = challengeQuestionValues.get("realm");
            final String qop = challengeQuestionValues.get("qop");

            final MessageDigest md5 = MessageDigest.getInstance(DEFAULT_ALGORITHM);
            final SecureRandom secureRandom = new SecureRandom();

            final String a1 = printHexBinary(md5.digest(String.format("%s:%s:%s", proxyUserName, realm, proxyPassword).getBytes(UTF_8)));
            final String a2 = printHexBinary(md5.digest(String.format("%s:%s", Constants.CONNECT, uri).getBytes(UTF_8)));

            final byte[] cnonceBytes = new byte[16];
            secureRandom.nextBytes(cnonceBytes);
            final String cnonce = printHexBinary(cnonceBytes);

            String response;
            if (StringUtils.isNullOrEmpty(qop)) {
                response = printHexBinary(md5.digest(String.format("%s:%s:%s", a1, nonce, a2).getBytes(UTF_8)));
                digestValue = String.format("Digest username=\"%s\",realm=\"%s\",nonce=\"%s\",uri=\"%s\",cnonce=\"%s\",response=\"%s\"",
                        proxyUserName, realm, nonce, uri, cnonce, response);
            } else {
                int nc = nonceCounter.incrementAndGet();

                response = printHexBinary(md5.digest(String.format("%s:%s:%08X:%s:%s:%s", a1, nonce, nc, cnonce, qop, a2).getBytes(UTF_8)));

                digestValue = String.format(
                        "Digest username=\"%s\",realm=\"%s\",nonce=\"%s\",uri=\"%s\",cnonce=\"%s\",nc=%08X,response=\"%s\",qop=\"%s\"",
                        proxyUserName, realm, nonce, uri, cnonce, nc, response, qop);
            }

            headers.put(Constants.PROXY_AUTHORIZATION, digestValue);

            if (logger.isInfoEnabled()) {
                logger.info("Adding authorization header. {} '{}'", Constants.PROXY_AUTHORIZATION, digestValue);
            }
        } catch (NoSuchAlgorithmException ex) {
            if (logger.isErrorEnabled()) {
                logger.error("Error encountered when computing header.", ex);
            }

            throw new RuntimeException(ex);
        }
    }

    static String printHexBinary(byte[] data) {
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data) {
            r.append(HEX_CODE[(b >> 4) & 0xF]);
            r.append(HEX_CODE[(b & 0xF)]);
        }
        return r.toString().toLowerCase(Locale.ROOT);
    }
}
