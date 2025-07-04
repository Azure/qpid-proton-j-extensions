// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.Proxy;
import com.microsoft.azure.proton.transport.proxy.ProxyAuthenticationType;
import com.microsoft.azure.proton.transport.proxy.ProxyChallengeProcessor;
import com.microsoft.azure.proton.transport.proxy.ProxyConfiguration;
import com.microsoft.azure.proton.transport.proxy.ProxyHandler;
import com.microsoft.azure.proton.transport.proxy.ProxyResponse;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.microsoft.azure.proton.transport.proxy.ProxyAuthenticationType.BASIC;
import static com.microsoft.azure.proton.transport.proxy.ProxyAuthenticationType.DIGEST;
import static com.microsoft.azure.proton.transport.proxy.impl.Constants.PROXY_AUTHENTICATE;
import static com.microsoft.azure.proton.transport.proxy.impl.Constants.PROXY_CONNECT_FAILED;
import static com.microsoft.azure.proton.transport.proxy.impl.Constants.PROXY_CONNECT_USER_ERROR;
import static com.microsoft.azure.proton.transport.proxy.impl.Constants.PROXY_HANDSHAKE_BUFFER_SIZE;
import static org.apache.qpid.proton.engine.impl.ByteBufferUtils.newWriteableBuffer;

/**
 * Implementation class that handles connecting to, the status of, and passing bytes through the web socket after the
 * proxy is created.
 *
 * @see Proxy
 * @see ProxyHandler
 */
public class ProxyImpl implements Proxy, TransportLayer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyImpl.class);

    private final ByteBuffer inputBuffer;
    private final ByteBuffer outputBuffer;
    private final ProxyConfiguration proxyConfiguration;

    private boolean tailClosed = false;
    private boolean headClosed = false;
    private String host = "";
    private volatile Map<String, String> headers = null;
    private TransportImpl underlyingTransport;
    private ProxyHandler proxyHandler;

    private volatile boolean isProxyConfigured;
    private volatile ProxyState proxyState;
    private volatile boolean respondToChallengeOnNewConnection = false;
    private final AtomicReference<State> fromState = new AtomicReference<>(null);

    /**
     * Create proxy transport layer - which, after configuring using the {@link #configure(String, Map, ProxyHandler,
     * Transport)} API is ready for layering in qpid-proton-j transport layers, using {@link
     * org.apache.qpid.proton.engine.impl.TransportInternal#addTransportLayer(TransportLayer)} API.
     */
    public ProxyImpl() {
        this(null);
    }

    /**
     * Create proxy transport layer - which, after configuring using the {@link #configure(String, Map, ProxyHandler,
     * Transport)} API is ready for layering in qpid-proton-j transport layers, using {@link
     * org.apache.qpid.proton.engine.impl.TransportInternal#addTransportLayer(TransportLayer)} API.
     *
     * @param configuration Proxy configuration to use.
     */
    public ProxyImpl(ProxyConfiguration configuration) {
        inputBuffer = newWriteableBuffer(PROXY_HANDSHAKE_BUFFER_SIZE);
        outputBuffer = newWriteableBuffer(PROXY_HANDSHAKE_BUFFER_SIZE);
        isProxyConfigured = false;
        proxyConfiguration = configuration;
    }

    /**
     * Adds the proxy in the transport layer chain.
     *
     * @param input The input to the transport layer.
     * @param output The output from the transport layer.
     *
     * @return A transport layer containing the proxy.
     */
    @Override
    public TransportWrapper wrap(TransportInput input, TransportOutput output) {
        return new ProxyTransportWrapper(input, output);
    }

    /**
     * Configures the AMQP broker {@code host} with the given proxy handler and transport.
     *
     * @param host AMQP broker.
     * @param headers Additional headers to add to the proxy request.
     * @param proxyHandler Handler for the proxy.
     * @param underlyingTransport Actual transport layer.
     */
    @Override
    public void configure(
            String host,
            Map<String, String> headers,
            ProxyHandler proxyHandler,
            Transport underlyingTransport) {
        this.host = host;
        final State source = this.fromState.get();
        if (source != null) {
            // restore the state from the previous proxy to resume.
            this.headers = source.headers;
            this.proxyState = source.proxyState;
        } else {
            this.headers = headers;
            this.proxyState = ProxyState.PN_PROXY_NOT_STARTED;
        }
        this.proxyHandler = proxyHandler;
        this.underlyingTransport = (TransportImpl) underlyingTransport;
        isProxyConfigured = true;
    }

    /**
     * Gets headers for the proxy request.
     *
     * @return Headers for the proxy request.
     */
    public Map<String, String> getProxyRequestHeaders() {
        return this.headers;
    }

    /**
     * transfers the state from the given proxy {@code fromProxy} to this proxy.
     *
     * @param fromProxy Proxy to transfer state from.
     */
    public void transferState(ProxyImpl fromProxy) {
        if (fromProxy.respondToChallengeOnNewConnection) {
            LOGGER.debug("Transferring state from proxy {} {} {}", this.hashCode(), System.lineSeparator(), fromProxy.headers);
            this.fromState.set(State.from(fromProxy));
        }
    }

    /**
     * Buffer to read responses from.
     *
     * @return Buffer to read responses from.
     */
    protected ByteBuffer getInputBuffer() {
        return this.inputBuffer;
    }

    /**
     * Buffer to write responses and requests to.
     *
     * @return Buffer to write responses and requests to.
     */
    protected ByteBuffer getOutputBuffer() {
        return this.outputBuffer;
    }

    /**
     * Whether settings to connect to a proxy are configured.
     *
     * @return true if settings are configured, false otherwise.
     */
    protected boolean getIsProxyConfigured() {
        return this.isProxyConfigured;
    }

    /**
     * Gets the handler to respond to challenges from the proxy.
     *
     * @return the handler to respond to challenges from the proxy.
     */
    protected ProxyHandler getProxyHandler() {
        return this.proxyHandler;
    }

    /**
     * Gets the next transport layer in the chain qpid-proton-j uses.
     *
     * @return The next transport layer in the chain qpid-proton-j uses.
     */
    protected Transport getUnderlyingTransport() {
        return this.underlyingTransport;
    }

    /**
     * Writes the CONNECT request.
     */
    protected void writeProxyRequest() {
        outputBuffer.clear();
        final String request = proxyHandler.createProxyRequest(host, headers);

        LOGGER.info("Writing proxy request:{} {}{}{}", this.hashCode(), "State: " + proxyState, System.lineSeparator(), request);

        //TODO (conniey): HTTP headers are encoded using StandardCharsets.ISO_8859_1. update proxyHandler.createProxyRequest to return bytes instead
        // of String because encoding is not UTF-16. https://stackoverflow.com/a/655948/4220757
        // See https://datatracker.ietf.org/doc/html/rfc2616#section-3.7.1
        outputBuffer.put(request.getBytes());
    }

    /**
     * Whether handshake is in progress.
     *
     * @return True if handshake is in progress.
     */
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

    /**
     * Gets the current state of the proxy.
     *
     * @return State of the proxy.
     */
    protected ProxyState getProxyState() {
        return this.proxyState;
    }

    private class ProxyTransportWrapper implements TransportWrapper {
        private final TransportInput underlyingInput;
        private final TransportOutput underlyingOutput;
        private final ByteBuffer head;

        // Represents a response from a CONNECT request.
        private final AtomicReference<ProxyResponse> proxyResponse = new AtomicReference<>();

        /**
         * Creates a transport wrapper that wraps the WebSocket transport input and output.
         */
        ProxyTransportWrapper(TransportInput input, TransportOutput output) {
            underlyingInput = input;
            underlyingOutput = output;
            head = outputBuffer.asReadOnlyBuffer();
            head.limit(0);
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

                    final ProxyResponse connectResponse = readProxyResponse(inputBuffer);

                    if (connectResponse == null || connectResponse.isMissingContent()) {
                        LOGGER.info("Request is missing content. Waiting for more bytes.");
                        break;
                    }

                    // Clean up response to prepare for challenge
                    proxyResponse.set(null);

                    final boolean isSuccess = proxyHandler.validateProxyResponse(connectResponse);
                    // When connecting to proxy, it does not challenge us for authentication. If the user has specified
                    // a configuration, and it is not NONE, then we fail due to misconfiguration.
                    if (isSuccess) {
                        if (proxyConfiguration == null || proxyConfiguration.authentication() == ProxyAuthenticationType.NONE) {
                            proxyState = ProxyState.PN_PROXY_CONNECTED;
                        } else {
                            if (LOGGER.isErrorEnabled()) {
                                LOGGER.error("ProxyConfiguration mismatch. User configured: '{}', but authentication is not required",
                                    proxyConfiguration.authentication());
                            }
                            closeTailProxyError(PROXY_CONNECT_USER_ERROR);
                        }
                        break;
                    }

                    final Map<String, List<String>> headers = connectResponse.getHeaders();
                    final Set<ProxyAuthenticationType> supportedTypes = getAuthenticationTypes(headers);

                    // The proxy did not successfully connect, user has specified that they want a particular
                    // authentication method, but it is not in list of supported authentication methods.
                    if (proxyConfiguration != null && !supportedTypes.contains(proxyConfiguration.authentication())) {
                        if (LOGGER.isErrorEnabled()) {
                            LOGGER.error("Proxy authentication required. User configured: '{}', but supported proxy authentication methods are: {}",
                                proxyConfiguration.authentication(),
                                supportedTypes.stream().map(type -> type.toString()).collect(Collectors.joining(",")));
                        }
                        closeTailProxyError(PROXY_CONNECT_USER_ERROR + PROXY_CONNECT_FAILED
                                + connectResponse);
                        break;
                    }

                    final List<String> challenges = headers.getOrDefault(PROXY_AUTHENTICATE, new ArrayList<>());
                    final ProxyChallengeProcessor processor = proxyConfiguration != null
                            ? getChallengeProcessor(host, challenges, proxyConfiguration.authentication())
                            : getChallengeProcessor(host, challenges, supportedTypes);

                    if (processor != null) {
                        proxyState = ProxyState.PN_PROXY_CHALLENGE;
                        ProxyImpl.this.headers = processor.getHeader();
                        if (connectResponse.hasConnectionCloseHeader()) {
                            ProxyImpl.this.respondToChallengeOnNewConnection = true;
                            LOGGER.info("Proxy server closed the connection, attempting challenge response on new connection.");
                            closeTailProxyError("Proxy server closed the connection.");
                            break;
                        }
                    } else {
                        LOGGER.warn("Could not get ProxyChallengeProcessor for challenges.");
                        closeTailProxyError(PROXY_CONNECT_FAILED + String.join(";", challenges));
                    }

                    break;
                case PN_PROXY_CHALLENGE_RESPONDED:
                    inputBuffer.flip();
                    final ProxyResponse challengeResponse = readProxyResponse(inputBuffer);

                    if (challengeResponse == null || challengeResponse.isMissingContent()) {
                        LOGGER.warn("Request is missing content. Waiting for more bytes.");
                        break;
                    }
                    //Clean up
                    proxyResponse.set(null);

                    final boolean result = proxyHandler.validateProxyResponse(challengeResponse);

                    if (result) {
                        proxyState = ProxyState.PN_PROXY_CONNECTED;
                    } else {
                        closeTailProxyError(PROXY_CONNECT_FAILED + challengeResponse);
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
                    if (respondToChallengeOnNewConnection) {
                        return Transport.END_OF_STREAM;
                    }
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
                    if (respondToChallengeOnNewConnection) {
                        return Transport.END_OF_STREAM;
                    }
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

        /**
         * Gets the beginning of the output buffer.
         *
         * @return The beginning of the byte buffer.
         */
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

        /**
         * Removes the first number of bytes from the output buffer.
         *
         * @param bytes The number of bytes to remove from the output buffer.
         */
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

        /**
         * Closes the output transport.
         */
        @Override
        public void close_head() {
            headClosed = true;
            underlyingOutput.close_head();
        }

        /*
         * Gets the ProxyChallengeProcessor based on authentication types supported. Prefers DIGEST authentication if
         * supported over BASIC. Returns null if it cannot match any supported types.
         */
        private ProxyChallengeProcessor getChallengeProcessor(String host, List<String> challenges,
                                                              Set<ProxyAuthenticationType> authentication) {
            final ProxyAuthenticationType authType;
            if (authentication.contains(DIGEST)) {
                authType = DIGEST;
            } else if (authentication.contains(BASIC)) {
                authType = BASIC;
            } else {
                return null;
            }

            return getChallengeProcessor(host, challenges, authType);
        }

        private ProxyChallengeProcessor getChallengeProcessor(String host, List<String> challenges,
                                                              ProxyAuthenticationType authentication) {
            final ProxyAuthenticator authenticator = proxyConfiguration != null
                    ? new ProxyAuthenticator(proxyConfiguration)
                    : new ProxyAuthenticator();

            switch (authentication) {
                case DIGEST:
                    final Optional<String> matching = challenges.stream()
                            .filter(challenge -> challenge.toLowerCase(Locale.ROOT).startsWith(Constants.DIGEST_LOWERCASE))
                            .findFirst();

                    return matching.map(c -> new DigestProxyChallengeProcessorImpl(host, c, authenticator))
                            .orElse(null);
                case BASIC:
                    return new BasicProxyChallengeProcessorImpl(host, authenticator);
                default:
                    LOGGER.warn("Authentication type does not have a challenge processor: {}", authentication);
                    return null;
            }
        }

        /**
         * Gets the supported authentication types based on the {@code headers}.
         *
         * @param headers HTTP proxy response headers from service call.
         * @return The supported proxy authentication methods. Or, an empty set if the value of {@code error} is {@code
         *         null}, an empty string. Or, if it does not contain{@link Constants#PROXY_AUTHENTICATE} with
         *         {@link Constants#BASIC_LOWERCASE} or {@link Constants#DIGEST_LOWERCASE}.
         */
        private Set<ProxyAuthenticationType> getAuthenticationTypes(Map<String, List<String>> headers) {
            if (!headers.containsKey(PROXY_AUTHENTICATE)) {
                return Collections.emptySet();
            }

            final Set<ProxyAuthenticationType> supportedTypes = new HashSet<>();
            final List<String> authenticationTypes = headers.get(PROXY_AUTHENTICATE);

            for (String type : authenticationTypes) {
                final String lowercase = type.toLowerCase(Locale.ROOT);

                if (lowercase.startsWith(Constants.BASIC_LOWERCASE)) {
                    supportedTypes.add(BASIC);
                } else if (lowercase.startsWith(Constants.DIGEST_LOWERCASE)) {
                    supportedTypes.add(DIGEST);
                } else {
                    LOGGER.warn("Did not understand this authentication type: {}", type);
                }
            }

            return supportedTypes;
        }

        private void closeTailProxyError(String errorMessage) {
            tailClosed = true;
            underlyingTransport.closed(new TransportException(errorMessage));
        }

        /**
         * Given a byte buffer, reads a HTTP proxy response from it.
         *
         * @param buffer The buffer to read HTTP proxy response from.
         * @return The current HTTP proxy response. Or {@code null} if one could not be read from the buffer and there
         *         is no current HTTP response.
         */
        private ProxyResponse readProxyResponse(ByteBuffer buffer) {
            int size = buffer.remaining();
            if (size <= 0) {
                LOGGER.warn("InputBuffer is empty. Not reading any contents from it. Returning current response.");
                return proxyResponse.get();
            }

            ProxyResponse current = proxyResponse.get();
            if (current == null) {
                proxyResponse.set(ProxyResponseImpl.create(buffer));
            } else {
                current.addContent(buffer);
            }

            buffer.compact();

            return proxyResponse.get();
        }
    }

    private static final class State {
        final Map<String, String> headers;
        final ProxyState proxyState;

        static State from(ProxyImpl fromProxy) {
            return new State(new HashMap<>(fromProxy.headers), fromProxy.proxyState);
        }

        /**
         * Constructs a new State, used only from {@link State#from(ProxyImpl)} factory method.
         *
         * @param headers
         * @param proxyState
         */
        private State(Map<String, String> headers, ProxyState proxyState) {
            this.headers = headers;
            this.proxyState = proxyState;
        }
    }
}
