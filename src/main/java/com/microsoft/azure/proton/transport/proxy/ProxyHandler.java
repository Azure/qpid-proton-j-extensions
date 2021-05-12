/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Creates and validates proxy requests and responses.
 */
public interface ProxyHandler {

    class ProxyResponseResult {
        private Boolean isSuccess;
        private String error;

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
     * Verifies that {@code buffer} contains a successful CONNECT response.
     *
     * @param buffer Buffer containing the HTTP response.
     * @return Indicates if CONNECT response contained a success. If not, contains an error indicating why the call was
     *         not successful.
     */
    ProxyResponseResult validateProxyResponse(ByteBuffer buffer);
}
