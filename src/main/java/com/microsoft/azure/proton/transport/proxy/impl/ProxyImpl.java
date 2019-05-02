/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.Proxy;
import com.microsoft.azure.proton.transport.proxy.ProxyAuthenticationType;
import com.microsoft.azure.proton.transport.proxy.ProxyChallengeProcessor;
import com.microsoft.azure.proton.transport.proxy.ProxyHandler;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.TransportException;
import org.apache.qpid.proton.engine.impl.TransportImpl;
import org.apache.qpid.proton.engine.impl.TransportInput;
import org.apache.qpid.proton.engine.impl.TransportLayer;
import org.apache.qpid.proton.engine.impl.TransportOutput;
import org.apache.qpid.proton.engine.impl.TransportWrapper;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;

import static org.apache.qpid.proton.engine.impl.ByteBufferUtils.newWriteableBuffer;

public class ProxyImpl implements Proxy, TransportLayer {
    private static final String PROXY_CONNECT_FAILED = "Proxy connect request failed with error: ";
    private static final int PROXY_HANDSHAKE_BUFFER_SIZE = 8 * 1024; // buffers used only for proxy-handshake

    private final ByteBuffer inputBuffer;
    private final ByteBuffer outputBuffer;

    private boolean tailClosed = false;
    private boolean headClosed = false;
    private boolean isProxyConfigured;
    private String host = "";
    private Map<String, String> headers = null;
    private TransportImpl underlyingTransport;
    private ProxyState proxyState = ProxyState.PN_PROXY_NOT_STARTED;
    private ProxyConfiguration proxyConfiguration;
    private ProxyHandler proxyHandler;

    /**
     * Create proxy transport layer - which, after configuring using
     * the {@link #configure(String, Map, ProxyHandler, Transport)} API
     * is ready for layering in qpid-proton-j transport layers, using
     * {@link org.apache.qpid.proton.engine.impl.TransportInternal#addTransportLayer(TransportLayer)} API.
     */
    public ProxyImpl() {
        this(null);
    }

    /**
     * Create proxy transport layer - which, after configuring using
     * the {@link #configure(String, Map, ProxyHandler, Transport)} API
     * is ready for layering in qpid-proton-j transport layers, using
     * {@link org.apache.qpid.proton.engine.impl.TransportInternal#addTransportLayer(TransportLayer)} API.
     *
     * @param configuration Proxy configuration to use.
     */
    public ProxyImpl(ProxyConfiguration configuration) {
        inputBuffer = newWriteableBuffer(PROXY_HANDSHAKE_BUFFER_SIZE);
        outputBuffer = newWriteableBuffer(PROXY_HANDSHAKE_BUFFER_SIZE);
        isProxyConfigured = false;
        proxyConfiguration = configuration;
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
            if (!getIsHandshakeInProgress()) {
                underlyingInput.process();
                return;
            }

            switch (proxyState) {
                case PN_PROXY_CONNECTING:
                    inputBuffer.flip();
                    final ProxyHandler.ProxyResponseResult responseResult = proxyHandler
                            .validateProxyResponse(inputBuffer);
                    inputBuffer.compact();
                    inputBuffer.clear();
                    if (responseResult.getIsSuccess()) {
                        proxyState = ProxyState.PN_PROXY_CONNECTED;
                        break;
                    }

                    final String error = responseResult.getError();
                    final ProxyChallengeProcessor challengeProcessor = getChallengeProcessor(error, host, proxyConfiguration);

                    if (challengeProcessor != null) {
                        proxyState = ProxyState.PN_PROXY_CHALLENGE;
                        headers = challengeProcessor.getHeader();
                    } else {
                        tailClosed = true;
                        underlyingTransport.closed(new TransportException(PROXY_CONNECT_FAILED + error));
                    }
                    break;
                case PN_PROXY_CHALLENGE_RESPONDED:
                    inputBuffer.flip();
                    final ProxyHandler.ProxyResponseResult challengeResponseResult = proxyHandler
                            .validateProxyResponse(inputBuffer);
                    inputBuffer.compact();

                    if (challengeResponseResult.getIsSuccess()) {
                        proxyState = ProxyState.PN_PROXY_CONNECTED;
                    } else {
                        tailClosed = true;
                        underlyingTransport.closed(
                                new TransportException(PROXY_CONNECT_FAILED + challengeResponseResult.getError()));
                    }
                    break;
                default:
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
            if (!getIsHandshakeInProgress()) {
                return underlyingOutput.pending();
            }

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
                case PN_PROXY_CHALLENGE:
                    if (outputBuffer.position() == 0) {
                        proxyState = ProxyState.PN_PROXY_CHALLENGE_RESPONDED;
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
                case PN_PROXY_CHALLENGE_RESPONDED:
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
        }

        @Override
        public ByteBuffer head() {
            if (getIsHandshakeInProgress()) {
                switch (proxyState) {
                    case PN_PROXY_CONNECTING:
                    case PN_PROXY_CHALLENGE_RESPONDED:
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
                    case PN_PROXY_CHALLENGE_RESPONDED:
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

        /**
         * Gets the challenge processor given the challenge and host.
         *
         * @param challenge The challenge associated with this response.
         * @param host The host for this response.
         * @return The {@link ProxyChallengeProcessor} for this challenge.
         */
        private ProxyChallengeProcessor getChallengeProcessor(String challenge, String host, ProxyConfiguration configuration) {
            if (StringUtils.isNullOrEmpty(challenge)) {
                return null;
            }

            if (configuration != null) {
                return getChallengeProcessor(configuration.authentication(), host, challenge);
            }

            final ProxyAuthenticationType authenticationType = getAuthenticationType(challenge);

            return authenticationType != null
                    ? getChallengeProcessor(authenticationType, host, challenge)
                    : null;
        }

        /**
         * Gets authentication type based on the {@code error}.
         *
         * @param error Response from service call.
         * @return {@code null} if the value of {@code error} is {@code null}, an empty string. Also, if it does not
         * contain {@link Constants#PROXY_AUTHENTICATE_HEADER} with {@link Constants#BASIC_LOWERCASE} or
         * {@link Constants#DIGEST_LOWERCASE}.
         */
        private ProxyAuthenticationType getAuthenticationType(String error) {
            int index = error.indexOf(Constants.PROXY_AUTHENTICATE_HEADER);

            if (index == -1) {
                return null;
            }

            String challengeType = error.substring(index).trim().toLowerCase(Locale.ROOT);

            if (challengeType.contains(Constants.BASIC_LOWERCASE)) {
                return ProxyAuthenticationType.BASIC;
            } else if (challengeType.contains(Constants.DIGEST_LOWERCASE)) {
                return ProxyAuthenticationType.DIGEST;
            } else {
                return null;
            }
        }

        private ProxyChallengeProcessor getChallengeProcessor(ProxyAuthenticationType authentication, String host,
                                                              String challenge) {
            if (authentication == null) {
                return null;
            }

            switch (authentication) {
                case BASIC:
                    return new BasicProxyChallengeProcessorImpl(host);
                case DIGEST:
                    return new DigestProxyChallengeProcessorImpl(host, challenge);
                case NONE:
                default:
                    return null;
            }
        }
    }
}
