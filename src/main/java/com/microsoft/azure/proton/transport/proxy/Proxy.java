/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */
package com.microsoft.azure.proton.transport.proxy;

import java.util.Map;

public interface Proxy {
    public enum ProxyState {
        PN_PROXY_NOT_STARTED,
        PN_PROXY_CONNECTING,
        PN_PROXY_CONNECTED,
        PN_PROXY_FAILED
    }

    void configure(
            String host,
            Map<String, String> headers,
            ProxyHandler proxyHandler);
}
