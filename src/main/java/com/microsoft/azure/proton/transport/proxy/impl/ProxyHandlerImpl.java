/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.Proxy;
import com.microsoft.azure.proton.transport.proxy.ProxyHandler;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Implementation class that handles connecting to the proxy.
 *
 * @see Proxy
 * @see ProxyHandler
 */
public class ProxyHandlerImpl implements ProxyHandler {
    /**
     * CONNECT request format string initiated by ProxyHandler.
     */
    static final String CONNECT_REQUEST = "CONNECT %1$s HTTP/1.1%2$sHost: %1$s%2$sConnection: Keep-Alive%2$s";
    static final String HEADER_FORMAT = "%s: %s";
    static final String NEW_LINE = "\r\n";
    private final Pattern successStatusLine = Pattern.compile("^http/1\\.(0|1) (?<statusCode>2[0-9]{2})", Pattern.CASE_INSENSITIVE);
    private final Predicate<String> successStatusLinePredicate = successStatusLine.asPredicate();

    /**
     * {@inheritDoc}
     */
    @Override
    public String createProxyRequest(String hostName, Map<String, String> additionalHeaders) {
        final StringBuilder connectRequestBuilder = new StringBuilder();
        connectRequestBuilder.append(
                String.format(Locale.ROOT, CONNECT_REQUEST, hostName, NEW_LINE));

        if (additionalHeaders != null) {
            additionalHeaders.forEach((header, value) -> {
                connectRequestBuilder.append(String.format(HEADER_FORMAT, header, value));
                connectRequestBuilder.append(NEW_LINE);
            });
        }

        connectRequestBuilder.append(NEW_LINE);
        return connectRequestBuilder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProxyResponseResult validateProxyResponse(ByteBuffer buffer) {
        int size = buffer.remaining();
        String response = null;

        if (size > 0) {
            byte[] responseBytes = new byte[buffer.remaining()];
            buffer.get(responseBytes);
            response = new String(responseBytes, StandardCharsets.UTF_8);
            final Scanner responseScanner = new Scanner(response);
            if (responseScanner.hasNextLine()) {
                final String firstLine = responseScanner.nextLine();
                if (successStatusLinePredicate.test(firstLine)) {
                    return new ProxyResponseResult(true, null);
                }
            }
        }

        return new ProxyResponseResult(false, response);
    }
}
