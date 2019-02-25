/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy.impl;

import static org.apache.qpid.proton.engine.impl.ByteBufferUtils.newWriteableBuffer;

import com.microsoft.azure.proton.transport.proxy.Proxy;
import com.microsoft.azure.proton.transport.proxy.ProxyHandler;

import java.io.UnsupportedEncodingException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.TransportException;
import org.apache.qpid.proton.engine.impl.TransportImpl;
import org.apache.qpid.proton.engine.impl.TransportInput;
import org.apache.qpid.proton.engine.impl.TransportLayer;
import org.apache.qpid.proton.engine.impl.TransportOutput;
import org.apache.qpid.proton.engine.impl.TransportWrapper;

import javax.xml.bind.DatatypeConverter;

public class ProxyImpl implements Proxy, TransportLayer {
    private final int proxyHandshakeBufferSize = 2 * 1024 * 1024; // buffers used only for proxy-handshake
    private final ByteBuffer inputBuffer;
    private final ByteBuffer outputBuffer;

    private boolean tailClosed = false;
    private boolean headClosed = false;
    private boolean isProxyConfigured;
    private String host = "";
    private Map<String, String> headers = null;
    private TransportImpl underlyingTransport;
    private ProxyState proxyState = ProxyState.PN_PROXY_NOT_STARTED;

    private ProxyHandler proxyHandler;

    private final String PROXY_AUTH_DIGEST = "Proxy-Authenticate: Digest";
    private final String PROXY_AUTH_BASIC = "Proxy-Authenticate: Basic";
    private final AtomicInteger nonceCounter = new AtomicInteger(0);
    /**
     * Create proxy transport layer - which, after configuring using
     * the {@link #configure(String, Map, ProxyHandler, Transport)} API
     * is ready for layering in qpid-proton-j transport layers, using
     * {@link org.apache.qpid.proton.engine.impl.TransportInternal#addTransportLayer(TransportLayer)} API.
     */
    public ProxyImpl() {
        inputBuffer = newWriteableBuffer(proxyHandshakeBufferSize);
        outputBuffer = newWriteableBuffer(proxyHandshakeBufferSize);
        isProxyConfigured = false;
    }

    @Override
    public TransportWrapper wrap(TransportInput input, TransportOutput output) {
        return new ProxyTransportWrapper(input, output);
    }

    @Override
    public void configure(
            String host,
            Map<String, String> headers,
            ProxyHandler proxyHandler,
            Transport underlyingTransport) {
        this.host = host;
        this.headers = headers;
        this.proxyHandler = proxyHandler;
        this.underlyingTransport = (TransportImpl) underlyingTransport;
        isProxyConfigured = true;
    }

    protected ByteBuffer getInputBuffer() {
        return this.inputBuffer;
    }

    protected ByteBuffer getOutputBuffer() {
        return this.outputBuffer;
    }

    protected Boolean getIsProxyConfigured() {
        return this.isProxyConfigured;
    }

    protected ProxyHandler getProxyHandler() {
        return this.proxyHandler;
    }

    protected Transport getUnderlyingTransport() {
        return this.underlyingTransport;
    }

    protected void writeProxyRequest() {
        outputBuffer.clear();
        String request = proxyHandler.createProxyRequest(host, headers);
        outputBuffer.put(request.getBytes());
    }

    protected boolean getIsHandshakeInProgress() {
        // if handshake is in progress
        // we do not engage the underlying transportInput/transportOutput.
        // Only when, ProxyState == Connected - then we can start engaging
        // next TransportLayers.
        // So, InProgress includes - proxyState = failed as well.
        // return true - from the point when proxyImpl.configure() is invoked to
        // proxyState transitions to Connected.
        // returns false - in all other cases
        return isProxyConfigured && proxyState != ProxyState.PN_PROXY_CONNECTED;
    }

    protected ProxyState getProxyState() {
        return this.proxyState;
    }

    public Map<String, String> getProxyRequestHeaders() {
        return this.headers;
    }

    private class ProxyTransportWrapper implements TransportWrapper {
        private final TransportInput underlyingInput;
        private final TransportOutput underlyingOutput;
        private final ByteBuffer head;

        ProxyTransportWrapper(TransportInput input, TransportOutput output) {
            underlyingInput = input;
            underlyingOutput = output;
            head = outputBuffer.asReadOnlyBuffer();
        }

        @Override
        public int capacity() {
            if (getIsHandshakeInProgress()) {
                if (tailClosed) {
                    return Transport.END_OF_STREAM;
                } else {
                    return inputBuffer.remaining();
                }
            } else {
                return underlyingInput.capacity();
            }
        }

        @Override
        public int position() {
            if (getIsHandshakeInProgress()) {
                if (tailClosed) {
                    return Transport.END_OF_STREAM;
                } else {
                    return inputBuffer.position();
                }
            } else {
                return underlyingInput.position();
            }
        }

        @Override
        public ByteBuffer tail() throws TransportException {
            if (getIsHandshakeInProgress()) {
                return inputBuffer;
            } else {
                return underlyingInput.tail();
            }
        }

        @Override
        public void process() throws TransportException {
            if (getIsHandshakeInProgress()) {
                switch (proxyState) {
                    case PN_PROXY_CONNECTING:
                        inputBuffer.flip();
                        final ProxyHandler.ProxyResponseResult responseResult = proxyHandler
                                .validateProxyResponse(inputBuffer);
                        inputBuffer.compact();
                        inputBuffer.clear();
                        if (responseResult.getIsSuccess()) {
                            proxyState = ProxyState.PN_PROXY_CONNECTED;
                        } else if (responseResult.getError() != null &&
                                responseResult.getError().contains(PROXY_AUTH_DIGEST)) {
                            proxyState = ProxyState.PN_PROXY_NOT_STARTED;
                            final Scanner responseScanner = new Scanner(responseResult.getError());
                            final Map<String, String> challengeQuestionValues = new HashMap<String, String>();
                            while (responseScanner.hasNextLine()) {
                                String line = responseScanner.nextLine();
                                if (line.contains(PROXY_AUTH_DIGEST)){
                                    getChallengeQuestionHeaders(line, challengeQuestionValues);
                                    break;
                                }
                            }
                            computeDigestAuthHeader(challengeQuestionValues);
                        } else if (responseResult.getError() != null &&
                                responseResult.getError().contains(PROXY_AUTH_BASIC)) {
                            proxyState = ProxyState.PN_PROXY_NOT_STARTED;
                            computeBasicAuthHeader();
                        } else {
                            tailClosed = true;
                            underlyingTransport.closed(
                                    new TransportException(
                                            "proxy connect request failed with error: "
                                            + responseResult.getError()));
                        }
                        break;
                    default:
                        underlyingInput.process();
                }
            } else {
                underlyingInput.process();
            }
        }

        @Override
        public void close_tail() {
            tailClosed = true;
            if (getIsHandshakeInProgress()) {
                headClosed = true;
            }

            underlyingInput.close_tail();
        }

        @Override
        public int pending() {
            if (getIsHandshakeInProgress()) {
                switch (proxyState) {
                    case PN_PROXY_NOT_STARTED:
                        if (outputBuffer.position() == 0) {
                            proxyState = ProxyState.PN_PROXY_CONNECTING;
                            writeProxyRequest();

                            head.limit(outputBuffer.position());
                            if (headClosed) {
                                proxyState = ProxyState.PN_PROXY_FAILED;
                                return Transport.END_OF_STREAM;
                            } else {
                                return outputBuffer.position();
                            }
                        } else {
                            return outputBuffer.position();
                        }

                    case PN_PROXY_CONNECTING:
                        if (headClosed && (outputBuffer.position() == 0)) {
                            proxyState = ProxyState.PN_PROXY_FAILED;
                            return Transport.END_OF_STREAM;
                        } else {
                            return outputBuffer.position();
                        }

                    default:
                        return Transport.END_OF_STREAM;
                }
            } else {
                return underlyingOutput.pending();
            }
        }

        @Override
        public ByteBuffer head() {
            if (getIsHandshakeInProgress()) {
                switch (proxyState) {
                    case PN_PROXY_CONNECTING:
                        return head;
                    default:
                        return underlyingOutput.head();
                }
            } else {
                return underlyingOutput.head();
            }
        }

        @Override
        public void pop(int bytes) {
            if (getIsHandshakeInProgress()) {
                switch (proxyState) {
                    case PN_PROXY_CONNECTING:
                        if (outputBuffer.position() != 0) {
                            outputBuffer.flip();
                            outputBuffer.position(bytes);
                            outputBuffer.compact();
                            head.position(0);
                            head.limit(outputBuffer.position());
                        } else {
                            underlyingOutput.pop(bytes);
                        }
                        break;
                    default:
                        underlyingOutput.pop(bytes);
                }
            } else {
                underlyingOutput.pop(bytes);
            }
        }

        @Override
        public void close_head() {
            headClosed = true;
            underlyingOutput.close_head();
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

        private void computeBasicAuthHeader(){
            final PasswordAuthentication authentication = Authenticator.requestPasswordAuthentication(
                    "",
                    null,
                    0,
                    "https",
                    "Event Hubs client websocket proxy support",
                    "basic",
                    null,
                    Authenticator.RequestorType.PROXY);
            if (authentication == null) return;

            final String proxyUserName = authentication.getUserName();
            final String proxyPassword = authentication.getPassword() != null
                    ? new String(authentication.getPassword())
                    : null;
            if (isNullOrEmpty(proxyUserName) || isNullOrEmpty(proxyPassword))  return;

            final String usernamePasswordPair = proxyUserName + ":" + proxyPassword;
            if (headers == null)
                headers = new HashMap<String, String>();
            headers.put(
                    "Proxy-Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(usernamePasswordPair.getBytes()));
        }

        private void computeDigestAuthHeader(Map<String, String> challengeQuestionValues) {
            String uri = host;
            PasswordAuthentication passwordAuthentication = Authenticator.requestPasswordAuthentication(
                    "",
                    null,
                    0,
                    "https",
                    "Event Hubs client websocket proxy support",
                    "digest",
                    null,
                    Authenticator.RequestorType.PROXY);

            String username = passwordAuthentication.getUserName();
            String password =  passwordAuthentication.getPassword() != null
                    ? new String(passwordAuthentication.getPassword())
                    : null;

            String digestValue;
            try {
                SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
                random.setSeed(System.currentTimeMillis());
                byte[] nonceBytes = new byte[16];
                random.nextBytes(nonceBytes);

                String nonce = challengeQuestionValues.get("nonce");
                String realm = challengeQuestionValues.get("realm");
                String qop = challengeQuestionValues.get("qop");

                MessageDigest md5 = MessageDigest.getInstance("md5");
                SecureRandom secureRandom = new SecureRandom();
                String a1 = DatatypeConverter.printHexBinary(md5.digest(String.format("%s:%s:%s", username, realm, password).getBytes("UTF-8"))).toLowerCase();
                String a2 = DatatypeConverter.printHexBinary(md5.digest(String.format("%s:%s", "CONNECT", uri).getBytes("UTF-8"))).toLowerCase();

                byte[] cnonceBytes = new byte[16];
                secureRandom.nextBytes(cnonceBytes);
                String cnonce = DatatypeConverter.printHexBinary(cnonceBytes).toLowerCase();
                String response;
                if (qop == null || qop.isEmpty()) {
                    response = DatatypeConverter.printHexBinary(md5.digest(String.format("%s:%s:%s", a1, nonce, a2).getBytes("UTF-8"))).toLowerCase();
                    digestValue = String.format("Digest username=\"%s\",realm=\"%s\",nonce=\"%s\",uri=\"%s\",cnonce=\"%s\",response=\"%s\"",
                            username, realm, nonce, uri, cnonce, response);
                } else {
                    int nc = nonceCounter.incrementAndGet();
                    response = DatatypeConverter.printHexBinary(md5.digest(String.format("%s:%s:%08X:%s:%s:%s", a1, nonce, nc, cnonce, qop, a2).getBytes("UTF-8"))).toLowerCase();
                    digestValue = String.format("Digest username=\"%s\",realm=\"%s\",nonce=\"%s\",uri=\"%s\",cnonce=\"%s\",nc=%08X,response=\"%s\",qop=\"%s\"",
                            username, realm, nonce, uri, cnonce, nc, response, qop);
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
    }
}
