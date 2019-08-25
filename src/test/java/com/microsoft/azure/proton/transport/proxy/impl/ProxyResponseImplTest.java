/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.HttpStatusLine;
import com.microsoft.azure.proton.transport.proxy.ProxyResponse;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.microsoft.azure.proton.transport.proxy.impl.StringUtils.NEW_LINE;

public class ProxyResponseImplTest {
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_TEXT = "text/plain";
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final Charset ENCODING = StandardCharsets.UTF_8;
    private static final String HEADER_FORMAT = "%s: %s" + NEW_LINE;

    /**
     * Verifies that it successfully parses a valid HTTP response.
     */
    @Test
    public void validResponse() {
        // Arrange
        final String[] statusLine = new String[]{"HTTP/1.1", "200", "Connection Established"};
        final Map<String, String> headers = new HashMap<>();
        headers.put("FiddlerGateway", "Direct");
        headers.put("StartTime", "13:08:21.574");
        headers.put("Connection", "close");

        final String response = getTestResponse(statusLine, headers);
        final ByteBuffer contents = ENCODING.encode(response);

        // Act
        final ProxyResponse actual = ProxyResponseImpl.create(contents);

        // Assert
        final HttpStatusLine status = actual.getStatus();
        Assert.assertEquals("1.1", status.getProtocolVersion());
        Assert.assertEquals(statusLine[2], status.getReason());
        Assert.assertEquals(Integer.parseInt(statusLine[1]), status.getStatusCode());

        Assert.assertFalse(actual.isMissingContent());

        final Map<String, List<String>> actualHeaders = actual.getHeaders();
        Assert.assertEquals(headers.size(), actualHeaders.size());

        headers.forEach((key, value) -> {
            final List<String> actualValue = actualHeaders.get(key);

            Assert.assertTrue(actualHeaders.containsKey(key));
            Assert.assertNotNull(actualValue);
            Assert.assertEquals(1, actualValue.size());
            Assert.assertEquals(value, actualValue.get(0));
        });
    }

    /**
     * Verifies that an exception is thrown when the header is invalid. successfully parses a valid HTTP response.
     */
    @Test
    public void invalidHeader() {
        // Arrange
        final String[] statusLine = new String[]{"HTTP/1.1", "abc", "Connection Established"};
        final Map<String, String> headers = new HashMap<>();
        headers.put("FiddlerGateway", "Direct");
        headers.put("StartTime", "13:08:21.574");
        headers.put("Connection", "close");

        final String response = getTestResponse(statusLine, headers);
        final ByteBuffer contents = ENCODING.encode(response);

        // Act & Assert
        try {
            ProxyResponseImpl.create(contents);
        } catch (IllegalArgumentException e) {
            Assert.assertNotNull(e.getCause());
            Assert.assertEquals(NumberFormatException.class, e.getCause().getClass());
        }
    }

    /**
     * Verifies that we can parse an empty response.
     */
    @Test
    public void emptyResponse() {
        // Arrange
        final String emptyResponse = NEW_LINE + NEW_LINE;
        final ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put(emptyResponse.getBytes(ENCODING));
        buffer.flip();

        // Act
        final ProxyResponse response = ProxyResponseImpl.create(buffer);

        // Assert
        Assert.assertNull(response.getStatus());
        Assert.assertFalse(response.isMissingContent());
        Assert.assertNotNull(response.getHeaders());
        Assert.assertEquals(0, response.getHeaders().size());
        Assert.assertEquals(0, response.getContents().position());
    }

    private static String getTestResponse(String[] statusLine, Map<String, String> headers) {
        return getTestResponse(statusLine, headers, null);
    }

    /**
     * If there is content, {@link #CONTENT_LENGTH} and {@link #CONTENT_TYPE} headers are added to the {@code headers}
     * parameter.
     *
     * @param statusLine HTTP status line to create the proxy response with.
     * @param headers A set of headers to add to teh proxy response.
     * @param body Optional HTTP content body.
     * @return A string representing the contents of the HTTP response.
     */
    private static String getTestResponse(String[] statusLine, Map<String, String> headers, String body) {
        final ByteBuffer encoded;
        if (body != null) {
            encoded = ENCODING.encode(body);
            encoded.flip();
            final int size = encoded.remaining();

            headers.put(CONTENT_TYPE, CONTENT_TYPE_TEXT);
            headers.put(CONTENT_LENGTH, Integer.toString(size));
        }

        final StringBuilder formattedHeaders = headers.entrySet()
                .stream()
                .collect(StringBuilder::new,
                        (builder, entry) -> builder.append(String.format(HEADER_FORMAT, entry.getKey(), entry.getValue())),
                        StringBuilder::append);

        String response = String.join(NEW_LINE,
                String.join(" ", statusLine),
                formattedHeaders.toString(),
                NEW_LINE); // The empty new line that ends the HTTP headers.

        if (body != null) {
            response += body + NEW_LINE;
        }

        return response;
    }
}
