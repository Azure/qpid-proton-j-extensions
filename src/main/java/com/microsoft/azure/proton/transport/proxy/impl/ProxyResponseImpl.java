/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

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
import java.util.Locale;
import java.util.Map;

/**
 * Represents an HTTP response from a proxy.
 *
 * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html">RFC2616</a>
 */
public class ProxyResponseImpl implements ProxyResponse {
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyResponseImpl.class);

    private final HttpStatusLine status;
    private final Map<String, List<String>> headers;
    private ByteBuffer contents;
    private WebSocketFrameReadState frameReadState;

    private ProxyResponseImpl(HttpStatusLine status, Map<String, List<String>> headers, ByteBuffer contents,
                              WebSocketFrameReadState frameReadState) {
        this.status = status;
        this.headers = headers;
        this.contents = contents;
        this.frameReadState = frameReadState;
    }

    static ProxyResponse create(ByteBuffer buffer) {
        // Because we've flipped the buffer, position = 0, and the limit = size of the content.
        int size = buffer.remaining();

        if (size <= 0) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "Cannot create a buffer with no items in it. Limit: %s. Position: %s. Cap: %s",
                    buffer.limit(), buffer.position(), buffer.capacity()));
        }

        final byte[] responseBytes = new byte[size];
        buffer.get(responseBytes);

        final String response = new String(responseBytes, StandardCharsets.UTF_8);
        final String[] lines = response.split(StringUtils.NEW_LINE);
        final Map<String, List<String>> headers = new HashMap<>();

        WebSocketFrameReadState frameReadState = WebSocketFrameReadState.INIT_READ;
        HttpStatusLine statusLine = null;
        ByteBuffer contents = ByteBuffer.allocate(0);

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
                            return new ProxyResponseImpl(statusLine, headers, contents, WebSocketFrameReadState.INIT_READ);
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

        frameReadState = contents.hasRemaining()
                ? WebSocketFrameReadState.CONTINUED_FRAME_READ
                : WebSocketFrameReadState.INIT_READ;

        return new ProxyResponseImpl(statusLine, headers, contents, frameReadState);
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
     */
    public void addContent(ByteBuffer content) {
        int size = content.remaining();

        if (size <= 0) {
            LOGGER.warn("There was no content to add to current HTTP response.");
            return;
        }

        final byte[] responseBytes = new byte[content.remaining()];
        content.get(responseBytes);

        this.contents.put(responseBytes);
    }

    private static Map.Entry<String, String> parseHeader(String contents) {
        final String[] split = contents.split(":", 2);

        if (split.length != 2) {
            throw new IllegalStateException("Line is not a valid header. Contents: " + contents);
        }

        return new AbstractMap.SimpleEntry<>(split[0].trim(), split[1].trim());
    }
}
