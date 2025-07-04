// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.HttpStatusLine;
import com.microsoft.azure.proton.transport.proxy.ProxyResponse;
import com.microsoft.azure.proton.transport.ws.WebSocket.WebSocketFrameReadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.microsoft.azure.proton.transport.proxy.impl.Constants.CONTENT_LENGTH;

/**
 * Represents an HTTP response from a proxy.
 *
 * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html">RFC2616</a>
 */
public final class ProxyResponseImpl implements ProxyResponse {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyResponseImpl.class);

    private final HttpStatusLine status;
    private final Map<String, List<String>> headers;
    private final ByteBuffer contents;

    private ProxyResponseImpl(HttpStatusLine status, Map<String, List<String>> headers, ByteBuffer contents) {
        this.status = status;
        this.headers = headers;
        this.contents = contents;
    }

    /**
     * Create a proxy response from a given {@code buffer}. Assumes that the {@code buffer} has been flipped.
     *
     * @param buffer Buffer which could parse to a proxy response.
     * @return A new instance of {@link ProxyResponseImpl} representing the given buffer.
     * @throws IllegalArgumentException if {@code buffer} have no content to read.
     */
    public static ProxyResponse create(ByteBuffer buffer) {
        // Because we've flipped the buffer, position = 0, and the limit = size of the content.
        int size = buffer.remaining();

        if (size <= 0) {
            throw new IllegalArgumentException(String.format("Cannot create response with buffer have no content. "
                + "Limit: %s. Position: %s. Cap: %s", buffer.limit(), buffer.position(), buffer.capacity()));
        }

        final byte[] responseBytes = new byte[size];
        buffer.get(responseBytes);

        final String response = new String(responseBytes, StandardCharsets.UTF_8);
        final String[] lines = response.split(StringUtils.NEW_LINE);
        final Map<String, List<String>> headers = new HashMap<>();

        WebSocketFrameReadState frameReadState = WebSocketFrameReadState.INIT_READ;
        HttpStatusLine statusLine = null;
        ByteBuffer contents = ByteBuffer.allocate(0);

        //Assume the full header message is in the first frame
        for (String line : lines) {
            switch (frameReadState) {
                case INIT_READ:
                    statusLine = HttpStatusLine.create(line);
                    frameReadState = WebSocketFrameReadState.CHUNK_READ;
                    break;
                case CHUNK_READ:
                    if (StringUtils.isNullOrEmpty(line)) {
                        // Now that we're done reading all the headers, figure out the size of the HTTP body and
                        // allocate an array of that size.
                        int length = 0;
                        if (headers.containsKey(CONTENT_LENGTH)) {
                            final List<String> contentLength = headers.get(CONTENT_LENGTH);
                            length = Integer.parseInt(contentLength.get(0));
                        }

                        boolean hasBody = length > 0;
                        if (!hasBody) {
                            LOGGER.info("There is no content in the response. Response: {}", response);
                            return new ProxyResponseImpl(statusLine, headers, contents);
                        }

                        contents = ByteBuffer.allocate(length);
                        frameReadState = WebSocketFrameReadState.HEADER_READ;
                    } else {
                        final Map.Entry<String, String> header = parseHeader(line);
                        final List<String> value = headers.getOrDefault(header.getKey(), new ArrayList<>());

                        value.add(header.getValue());
                        headers.put(header.getKey(), value);
                    }
                    break;
                case HEADER_READ:
                    if (contents.position() == 0) {
                        frameReadState = WebSocketFrameReadState.CONTINUED_FRAME_READ;
                    }

                    contents.put(line.getBytes(StandardCharsets.UTF_8));
                    contents.mark();
                    break;
                case CONTINUED_FRAME_READ:
                    contents.put(line.getBytes(StandardCharsets.UTF_8));
                    contents.mark();
                    break;
                default:
                    LOGGER.error("Unknown state: {}. Response: {}", frameReadState, response);
                    frameReadState = WebSocketFrameReadState.READ_ERROR;
                    break;
            }
        }


        return new ProxyResponseImpl(statusLine, headers, contents);
    }

    private static Map.Entry<String, String> parseHeader(String contents) {
        final String[] split = contents.split(":", 2);

        if (split.length != 2) {
            throw new IllegalStateException("Line is not a valid header. Contents: " + contents);
        }

        return new AbstractMap.SimpleEntry<>(split[0].trim(), split[1].trim());
    }

    /**
     * {@inheritDoc}
     */
    public HttpStatusLine getStatus() {
        return status;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * {@inheritDoc}
     */
    public ByteBuffer getContents() {
        return contents.duplicate();
    }

    /**
     * {@inheritDoc}
     */
    public String getError() {
        final ByteBuffer readonly = contents.asReadOnlyBuffer();
        readonly.flip();
        return StandardCharsets.UTF_8.decode(readonly).toString();
    }

    /**
     * Gets whether or not the HTTP response is complete. An HTTP response is complete when the HTTP header and body are
     * received.
     *
     * @return {@code true} if the HTTP response is complete, and {@code false} otherwise.
     */
    public boolean isMissingContent() {
        return contents.hasRemaining();
    }

    /**
     * Adds additional content to the HTTP response's body. Assumes that the {@code content} has been flipped.
     *
     * @param content Content to add to the body of the HTTP response.
     * @throws NullPointerException if {@code content} is {@code null}.
     * @throws IllegalArgumentException if {@code content} have no content to read.
     */
    public void addContent(ByteBuffer content) {
        Objects.requireNonNull(content, "'content' cannot be null.");

        int size = content.remaining();

        if (size <= 0) {
            throw new IllegalArgumentException("There was no content to add to current HTTP response.");
        }

        final byte[] responseBytes = new byte[content.remaining()];
        content.get(responseBytes);

        this.contents.put(responseBytes);
    }

    /**
     * Checks if the HTTP response for CONNECT has a "Connection: close" header.
     *
     * @return {@code true} if the HTTP response has a "Connection: close" header, and {@code false} otherwise.
     */
    @Override
    public boolean hasConnectionCloseHeader() {
        final String headerKey = headers.containsKey("Connection") ? "Connection" : "connection";
        if (headers.containsKey(headerKey)) {
            final List<String> connectionHeaders = headers.get(headerKey);
            if (connectionHeaders != null && !connectionHeaders.isEmpty()) {
                for (String header : connectionHeaders) {
                    if (header.equalsIgnoreCase("close")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
