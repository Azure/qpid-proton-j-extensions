// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.microsoft.azure.proton.transport.proxy.impl;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.microsoft.azure.proton.transport.proxy.impl.StringUtils.NEW_LINE;

final class TestUtils {
    /**
     * Encoding for the HTTP proxy response.
     */
    static final Charset ENCODING = StandardCharsets.UTF_8;

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_TEXT = "text/plain";
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final String HEADER_FORMAT = "%s: %s" + NEW_LINE;

    private TestUtils() {
    }

    /**
     * Creates a proxy HTTP response and returns it as a string.
     *
     * @param statusLine HTTP status line to create the proxy response with.
     * @param headers A set of headers to add to the proxy response.
     * @return A string representing the contents of the HTTP response.
     */
    static String createProxyResponse(String[] statusLine, Map<String, List<String>> headers) {
        return createProxyResponse(statusLine, headers, null);
    }

    /**
     * Creates a proxy HTTP response and returns it as a string. If there is content, {@link #CONTENT_LENGTH} and
     * {@link #CONTENT_TYPE} headers are added to the {@code headers} parameter.
     *
     * @param statusLine HTTP status line to create the proxy response with.
     * @param headers A set of headers to add to the proxy response.
     * @param body Optional HTTP content body.
     * @return A string representing the contents of the HTTP response.
     */
    static String createProxyResponse(String[] statusLine, Map<String, List<String>> headers, String body) {
        final ByteBuffer encoded;
        if (body != null) {
            //Add empty line to end body
            body += NEW_LINE;

            encoded = ENCODING.encode(body);
            final int size = encoded.remaining();

            headers.put(CONTENT_TYPE, Collections.singletonList(CONTENT_TYPE_TEXT));
            headers.put(CONTENT_LENGTH, Collections.singletonList(Integer.toString(size)));
        }

        final StringBuilder formattedHeaders = headers.entrySet()
                .stream()
                .collect(StringBuilder::new,
                        (builder, entry) -> entry.getValue()
                            .forEach(value -> builder.append(String.format(HEADER_FORMAT, entry.getKey(), value))),
                        StringBuilder::append);

        String response = String.join(NEW_LINE,
                String.join(" ", statusLine),
                formattedHeaders.toString(),
                NEW_LINE); // The empty new line that ends the HTTP headers.

        if (body != null) {
            response += body;
        }

        return response;
    }

}
