// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.proxy;

import com.microsoft.azure.proton.transport.proxy.impl.ChallengeResponseAccessHelper;

import java.util.List;
import java.util.Map;

/**
 * Represents the 407 challenge response from the proxy server.
 */
public final class ChallengeResponse {
    static {
        ChallengeResponseAccessHelper.setAccessor(ChallengeResponse::new);
    }
    private static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";
    private final Map<String, List<String>> headers;

    /**
     * Creates the ChallengeResponse.
     *
     * @param headers the response headers
     */
    ChallengeResponse(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    /**
     * Gets the headers.
     *
     * @return the headers.
     */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * Gets the authentication schemes supported by the proxy server.
     *
     * @return the authentication schemes supported by the proxy server.
     */
    public List<String> getAuthenticationSchemes() {
        return headers.get(PROXY_AUTHENTICATE);
    }
}
