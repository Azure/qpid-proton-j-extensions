// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.proxy;

import java.util.Map;

/**
 * Creates and validates proxy requests and responses.
 */
public interface ProxyHandler {

    /**
     * Creates a CONNECT request to the provided {@code hostName} and adds {@code additionalHeaders} to the request.
     *
     * @param hostName Name of the host to connect to.
     * @param additionalHeaders Optional. Additional headers to add to the request.
     * @return A string representing the HTTP CONNECT request.
     */
    String createProxyRequest(String hostName, Map<String, String> additionalHeaders);

    /**
     * Verifies that {@code httpResponse} contains a successful CONNECT response.
     *
     * @param httpResponse HTTP response to validate for a successful CONNECT response.
     * @return {@code true} if the HTTP response is successful and correct, and {@code false} otherwise.
     *
     */
    boolean validateProxyResponse(ProxyResponse httpResponse);
}
