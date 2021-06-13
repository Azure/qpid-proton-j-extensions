// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.proxy;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Creates and validates proxy requests and responses.
 */
public interface ProxyHandler {

    /**
     * Represents a response from the proxy.
     */
    class ProxyResponseResult {
        private final Boolean isSuccess;
        private final String error;

        /**
         * Creates a new response.
         *
         * @param isSuccess {@code true} if it was successful; {@code false} otherwise.
         * @param error The error from the proxy. Or {@code null} if there was none.
         */
        public ProxyResponseResult(final Boolean isSuccess, final String error) {
            this.isSuccess = isSuccess;
            this.error = error;
        }

        public Boolean getIsSuccess() {
            return isSuccess;
        }

        public String getError() {
            return error;
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
     * Creates a CONNECT request to the provided {@code hostName} and adds {@code additionalHeaders} to the request.
     *
     * @param hostName Name of the host to connect to.
     * @param additionalHeaders Optional. Additional headers to add to the request.
     * @return A byte array stream representing the HTTP CONNECT request.
     */
    byte[] createProxyRequestStream(String hostName, Map<String, String> additionalHeaders);

    /**
     * Verifies that {@code buffer} contains a successful CONNECT response.
     *
     * @param buffer Buffer containing the HTTP response.
     * @return Indicates if CONNECT response contained a success. If not, contains an error indicating why the call was
     *         not successful.
     */
    ProxyResponseResult validateProxyResponse(ByteBuffer buffer);
}
