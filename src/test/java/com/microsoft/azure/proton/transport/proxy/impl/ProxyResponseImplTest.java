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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.microsoft.azure.proton.transport.proxy.impl.StringUtils.NEW_LINE;

public class ProxyResponseImplTest {
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

        final String response = TestUtils.createProxyResponse(statusLine, headers);
        final ByteBuffer contents = TestUtils.ENCODING.encode(response);

        // Act
        final ProxyResponse actual = ProxyResponseImpl.create(contents);

        // Assert
        Assert.assertNotNull(actual);

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

        final String response = TestUtils.createProxyResponse(statusLine, headers);
        final ByteBuffer contents = TestUtils.ENCODING.encode(response);

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
        buffer.put(emptyResponse.getBytes(TestUtils.ENCODING));
        buffer.flip();

        // Act
        final ProxyResponse response = ProxyResponseImpl.create(buffer);

        // Assert
        Assert.assertNotNull(response);
        Assert.assertNull(response.getStatus());
        Assert.assertFalse(response.isMissingContent());
        Assert.assertNotNull(response.getHeaders());
        Assert.assertEquals(0, response.getHeaders().size());
        Assert.assertEquals(0, response.getContents().position());
    }
}
