/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy;

import org.apache.qpid.proton.engine.Transport;

import java.util.Map;

/**
 * Represents a proxy.
 */
public interface Proxy {
    /**
     * States that the proxy can be in.
     */
    enum ProxyState {
        PN_PROXY_NOT_STARTED,
        PN_PROXY_CONNECTING,
        PN_PROXY_CHALLENGE,
        PN_PROXY_CHALLENGE_RESPONDED,
        PN_PROXY_CONNECTED,
        PN_PROXY_FAILED
    }

    /**
     * Configures the AMQP broker {@code host} with the given proxy handler and transport.
     *
     * @param host AMQP broker.
     * @param headers Additional headers to add to the proxy request.
     * @param proxyHandler Handler for the proxy.
     * @param underlyingTransport Actual transport layer.
     */
    void configure(
            String host,
            Map<String, String> headers,
            ProxyHandler proxyHandler,
            Transport underlyingTransport);
}
