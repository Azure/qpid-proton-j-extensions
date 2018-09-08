/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */
package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.Proxy;
import com.microsoft.azure.proton.transport.proxy.ProxyHandler;
import com.microsoft.azure.proton.transport.ws.WebSocketHeader;

import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.TransportException;
import org.apache.qpid.proton.engine.impl.TransportInput;
import org.apache.qpid.proton.engine.impl.TransportLayer;
import org.apache.qpid.proton.engine.impl.TransportOutput;
import org.apache.qpid.proton.engine.impl.TransportWrapper;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.apache.qpid.proton.engine.impl.ByteBufferUtils.newWriteableBuffer;

public class ProxyImpl implements Proxy, TransportLayer {
    private final int maxFrameSize = (4 * 1024) + (16 * WebSocketHeader.MED_HEADER_LENGTH_MASKED);
    private boolean tailClosed = false;
    private boolean headClosed = false;
    private boolean isProxyConfigured;
    private String host = "";
    private Map<String, String> headers = null;
    private ProxyState proxyState = ProxyState.PN_PROXY_NOT_STARTED;

    private ProxyHandler proxyHandler;
    private int proxyFrameSize = 0;

    public ProxyImpl() {
        isProxyConfigured = false;
    }

    @Override
    public TransportWrapper wrap(TransportInput input, TransportOutput output) {
        return new ProxyTransportWrapper(input, output);
    }

    @Override
    public void configure(String host, Map<String, String> headers, ProxyHandler proxyHandler) {
        this.host = host;
        this.headers = headers;
        this.proxyHandler = proxyHandler;
        isProxyConfigured = true;
    }

    private class ProxyTransportWrapper implements TransportWrapper {
        private final TransportInput underlyingInput;
        private final TransportOutput underlyingOutput;
        private final ByteBuffer inputBuffer;
        private final ByteBuffer outputBuffer;
        private final ByteBuffer head;

        ProxyTransportWrapper(TransportInput input, TransportOutput output) {
            underlyingInput = input;
            underlyingOutput = output;
            inputBuffer = newWriteableBuffer(maxFrameSize);
            outputBuffer = newWriteableBuffer(maxFrameSize);
            head = outputBuffer.asReadOnlyBuffer();
        }

        private boolean isProxyNegotiationMode() {
            return isProxyConfigured
                    && (proxyState != ProxyState.PN_PROXY_CONNECTED || proxyState != ProxyState.PN_PROXY_FAILED);
        }

        protected void writeProxyRequest() {
            outputBuffer.clear();
            String request = proxyHandler.createProxyRequest(host, null);
            outputBuffer.put(request.getBytes());
        }

        @Override
        public int capacity() {
            if (isProxyNegotiationMode()) {
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
            if (isProxyNegotiationMode()) {
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
            if (isProxyNegotiationMode()) {
                return inputBuffer;
            } else {
                return underlyingInput.tail();
            }
        }

        @Override
        public void process() throws TransportException {
            if (isProxyNegotiationMode()) {
                inputBuffer.flip();

                switch (proxyState) {
                    case PN_PROXY_CONNECTING:
                        if (proxyHandler.validateProxyReply(inputBuffer)) {
                            proxyState = ProxyState.PN_PROXY_CONNECTED;
                        }
                        inputBuffer.compact();
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
            if (isProxyNegotiationMode()) {
                headClosed = true;
            }

            underlyingInput.close_tail();
        }

        @Override
        public int pending() {
            if (isProxyNegotiationMode()) {
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
            if (isProxyNegotiationMode()) {
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
            if (isProxyNegotiationMode()) {
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
                    case PN_PROXY_CONNECTED:
                    default:
                        underlyingOutput.pop(bytes);
                }
            } else {
                underlyingOutput.pop(bytes);
            }
        }

        @Override
        public void close_head() {
            underlyingOutput.close_head();
        }
    }
}
