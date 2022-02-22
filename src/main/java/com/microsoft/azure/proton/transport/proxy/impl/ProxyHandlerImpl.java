// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.HttpStatusLine;
import com.microsoft.azure.proton.transport.proxy.Proxy;
import com.microsoft.azure.proton.transport.proxy.ProxyHandler;
import com.microsoft.azure.proton.transport.proxy.ProxyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static final String CONNECTION_ESTABLISHED = "connection established";
    private static final Set<String> SUPPORTED_VERSIONS = Stream.of("1.1", "1.0").collect(Collectors.toSet());
    private final Logger logger = LoggerFactory.getLogger(ProxyHandlerImpl.class);

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
    public boolean validateProxyResponse(ProxyResponse response) {
        Objects.requireNonNull(response, "'response' cannot be null.");

        final HttpStatusLine status = response.getStatus();
        if (status == null) {
            logger.error("Response does not contain a status line. {}", response);
            return false;
        }

        return status.getStatusCode() == 200
                && SUPPORTED_VERSIONS.contains(status.getProtocolVersion())
                && CONNECTION_ESTABLISHED.equalsIgnoreCase(status.getReason());
    }
}
