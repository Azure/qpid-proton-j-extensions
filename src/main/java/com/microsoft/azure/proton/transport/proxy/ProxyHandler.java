// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.proxy;

import java.util.Map;
import java.util.Objects;

/**
 * Creates and validates proxy requests and responses.
 */
public interface ProxyHandler {

    /**
     * Represents a response from the proxy.
     */
    class ProxyResponseResult {
        private final boolean isSuccess;
        private final ProxyResponse response;

        /**
         * Creates a new response.
         *
         * @param isSuccess {@code true} if it was successful; {@code false} otherwise.
         * @param response The response from the proxy.
         */
        public ProxyResponseResult(final Boolean isSuccess, final ProxyResponse response) {
            this.isSuccess = isSuccess;
            this.response = Objects.requireNonNull(response, "'response' cannot be null.");
        }

        public boolean isSuccess() {
            return isSuccess;
        }

        public ProxyResponse getResponse() {
            return response;
        }

        public String getError() {
            return isSuccess ? null : response.getError();
        }
    }

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
     * @return Indicates if CONNECT response contained a success. If not, contains an error indicating why the call was
     *         not successful.
     */
    ProxyResponseResult validateProxyResponse(ProxyResponse httpResponse);
}
