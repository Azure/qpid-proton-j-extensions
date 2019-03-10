/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy.impl;

import static org.apache.qpid.proton.engine.impl.ByteBufferUtils.newWriteableBuffer;

import com.microsoft.azure.proton.transport.proxy.Proxy;
import com.microsoft.azure.proton.transport.proxy.ProxyHandler;

import java.nio.ByteBuffer;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.TransportException;
import org.apache.qpid.proton.engine.impl.TransportImpl;
import org.apache.qpid.proton.engine.impl.TransportInput;
import org.apache.qpid.proton.engine.impl.TransportLayer;
import org.apache.qpid.proton.engine.impl.TransportOutput;
import org.apache.qpid.proton.engine.impl.TransportWrapper;

public class ProxyImpl implements Proxy, TransportLayer {
    private final int proxyHandshakeBufferSize = 8 * 1024; // buffers used only for proxy-handshake
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
                        } else if (responseResult.getError() != null && responseResult.getError().contains(PROXY_AUTH_DIGEST)) {
                            proxyState = ProxyState.PN_PROXY_CHALLENGE;
                            DigestProxyChallengeProcessorImpl digestProxyChallengeProcessor = new DigestProxyChallengeProcessorImpl(host, responseResult.getError());
                            headers = digestProxyChallengeProcessor.getHeader();
                        } else if (responseResult.getError() != null && responseResult.getError().contains(PROXY_AUTH_BASIC)) {
                            proxyState = ProxyState.PN_PROXY_CHALLENGE;
                            BasicProxyChallengeProcessorImpl basicProxyChallengeProcessor = new BasicProxyChallengeProcessorImpl(host);
                            headers = basicProxyChallengeProcessor.getHeader();
                        } else {
                            tailClosed = true;
                            underlyingTransport.closed(
                                    new TransportException(
                                            "proxy connect request failed with error: "
                                            + responseResult.getError()));
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
                                    new TransportException(
                                            "proxy connect request failed with error: "
                                                    + challengeResponseResult.getError()));
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
                        if (headClosed && (outputBuffer.position() == 0)) {
                            proxyState = ProxyState.PN_PROXY_FAILED;
                            return Transport.END_OF_STREAM;
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

    }
}
