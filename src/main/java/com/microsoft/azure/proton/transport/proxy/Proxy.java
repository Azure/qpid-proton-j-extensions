// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

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
        /**
         * 1. No connection to proxy has been made.
         */
        PN_PROXY_NOT_STARTED,
        /**
         * 2. Sent initial CONNECT request to proxy.
         */
        PN_PROXY_CONNECTING,
        /**
         * 3. Proxy has responded with a Proxy-Authorization challenge.
         */
        PN_PROXY_CHALLENGE,
        /**
         * 4. Proxy has responded with a Proxy-Authorization challenge.
         */
        PN_PROXY_CHALLENGE_RESPONDED,
        /**
         * 5. Proxy has responded with a Proxy-Authorization challenge.
         */
        PN_PROXY_CONNECTED,
        /**
         * There has been a failure connecting to proxy.
         */
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
