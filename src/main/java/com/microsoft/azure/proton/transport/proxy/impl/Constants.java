package com.microsoft.azure.proton.transport.proxy.impl;

import java.util.Locale;

/**
 * Package private constants.
 */
class Constants {
    static final String PROXY_AUTHENTICATE_HEADER = "Proxy-Authenticate:";
    static final String PROXY_AUTHORIZATION = "Proxy-Authorization";

    static final String DIGEST = "Digest";
    static final String BASIC = "Basic";
    static final String BASIC_LOWERCASE = Constants.BASIC.toLowerCase(Locale.ROOT);
    static final String DIGEST_LOWERCASE = Constants.DIGEST.toLowerCase(Locale.ROOT);

    static final String CONNECT = "CONNECT";
}
