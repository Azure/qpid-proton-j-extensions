/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.Proxy;
import com.microsoft.azure.proton.transport.proxy.ProxyAuthenticationType;
import com.microsoft.azure.proton.transport.proxy.ProxyChallengeProcessor;
import com.microsoft.azure.proton.transport.proxy.ProxyConfiguration;
import com.microsoft.azure.proton.transport.proxy.ProxyHandler;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.TransportException;
import org.apache.qpid.proton.engine.impl.TransportImpl;
import org.apache.qpid.proton.engine.impl.TransportInput;
import org.apache.qpid.proton.engine.impl.TransportLayer;
import org.apache.qpid.proton.engine.impl.TransportOutput;
import org.apache.qpid.proton.engine.impl.TransportWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import static com.microsoft.azure.proton.transport.proxy.ProxyAuthenticationType.*;
import static org.apache.qpid.proton.engine.impl.ByteBufferUtils.newWriteableBuffer;

public class ProxyImpl implements Proxy, TransportLayer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyImpl.class);
    private static final String PROXY_CONNECT_FAILED = "Proxy connect request failed with error: ";
    private static final String PROXY_CONNECT_USER_ERROR = "User configuration error. Using non-matching proxy authentication." + PROXY_CONNECT_FAILED;
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

                    final String challenge = responseResult.getError();
                    final Set<ProxyAuthenticationType> supportedTypes = getAuthenticationTypes(challenge);

                    // The proxy did not successfully connect, user has specified that they want a particular
                    // authentication method, but it is not in list of supported authentication methods.
                    if (proxyConfiguration != null && !supportedTypes.contains(proxyConfiguration.authentication())) {
                        if (LOGGER.isErrorEnabled()) {
                            LOGGER.error("Proxy authentication required. User configured: '{}', but supported proxy authentication methods are: {}",
                                    proxyConfiguration.authentication(),
                                    supportedTypes.stream().map(Enum::toString).collect(Collectors.joining(",")));
                        }

                        //TODO conniey: Should we also also call close_tail() here?
                        tailClosed = true;
                        underlyingTransport.closed(new TransportException(PROXY_CONNECT_USER_ERROR + challenge));
                        break;
                    }

                    final ProxyChallengeProcessor processor = proxyConfiguration != null
                            ? getChallengeProcessor(challenge, host, proxyConfiguration.authentication())
                            : getChallengeProcessor(challenge, host, supportedTypes);

                    if (processor != null) {
                        proxyState = ProxyState.PN_PROXY_CHALLENGE;
                        headers = processor.getHeader();
                    } else {
                        tailClosed = true;
                        underlyingTransport.closed(new TransportException(PROXY_CONNECT_FAILED + challenge));
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
         * Gets the supported authentication types based on the {@code error}.
         *
         * @param error Response from service call.
         * @return The supported proxy authentication methods. Or, an empty array if the value of {@code error} is
         * {@code null}, an empty string. Also, if it does not contain {@link Constants#PROXY_AUTHENTICATE_HEADER} with
         * {@link Constants#BASIC_LOWERCASE} or {@link Constants#DIGEST_LOWERCASE}.
         */
        private Set<ProxyAuthenticationType> getAuthenticationTypes(String error) {
            int index = error.indexOf(Constants.PROXY_AUTHENTICATE_HEADER);

            if (index == -1) {
                return Collections.emptySet();
            }

            Set<ProxyAuthenticationType> supportedTypes = new HashSet<>();

            try (Scanner scanner = new Scanner(error)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();

                    if (!line.startsWith(Constants.PROXY_AUTHENTICATE_HEADER)) {
                        continue;
                    }

                    String substring = line.substring(Constants.PROXY_AUTHENTICATE_HEADER.length())
                            .trim().toLowerCase(Locale.ROOT);

                    if (substring.startsWith(Constants.BASIC_LOWERCASE)) {
                        supportedTypes.add(BASIC);
                    } else if (substring.startsWith(Constants.DIGEST_LOWERCASE)) {
                        supportedTypes.add(DIGEST);
                    }
                }
            }

            return supportedTypes;
        }

        /*
         * Gets the ProxyChallengeProcessor based on authentication types supported. Prefers DIGEST authentication if
         * supported over BASIC. Returns null if it cannot match any supported types.
         */
        private ProxyChallengeProcessor getChallengeProcessor(String host, String challenge,
                                                              Set<ProxyAuthenticationType> authentication) {
            if (authentication.contains(DIGEST)) {
                return getChallengeProcessor(host, challenge, DIGEST);
            } else if (authentication.contains(BASIC)) {
                return getChallengeProcessor(host, challenge, BASIC);
            } else {
                return null;
            }
        }

        private ProxyChallengeProcessor getChallengeProcessor(String host, String challenge,
                                                              ProxyAuthenticationType authentication) {
            final ProxyAuthenticator authenticator = proxyConfiguration != null
                    ? new ProxyAuthenticator(proxyConfiguration)
                    : new ProxyAuthenticator();

            switch (authentication) {
                case DIGEST:
                    return new DigestProxyChallengeProcessorImpl(host, challenge, authenticator);
                case BASIC:
                    return new BasicProxyChallengeProcessorImpl(host, authenticator);
                default:
                    return null;
            }
        }
    }
}
