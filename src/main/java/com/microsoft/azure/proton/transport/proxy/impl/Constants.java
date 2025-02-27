// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.proxy.impl;

import java.util.Locale;

/**
 * Package private constants.
 */
class Constants {
    static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";
    static final String PROXY_AUTHORIZATION = "Proxy-Authorization";

    static final String DIGEST = "Digest";
    static final String BASIC = "Basic";
    static final String BASIC_LOWERCASE = Constants.BASIC.toLowerCase(Locale.ROOT);
    static final String DIGEST_LOWERCASE = Constants.DIGEST.toLowerCase(Locale.ROOT);

    static final String CONNECT = "CONNECT";

    static final String PROXY_CONNECT_FAILED = "Proxy connect request failed with error: ";
    static final String PROXY_CONNECT_USER_ERROR = "User configuration error. Using non-matching proxy authentication.";

    static final int PROXY_HANDSHAKE_BUFFER_SIZE = 4 * 1024; // buffers used only for proxy-handshake

    static final String CONTENT_LENGTH = "Content-Length";
}
