/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */
package com.microsoft.azure.proton.transport.proxy;

import java.nio.ByteBuffer;
import java.util.Map;

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

    String createProxyRequest(String hostName, Map<String, String> additionalHeaders);

    ProxyResponseResult validateProxyResponse(ByteBuffer buffer);

}
