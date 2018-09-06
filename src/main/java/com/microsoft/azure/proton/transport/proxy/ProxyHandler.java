/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */
package com.microsoft.azure.proton.transport.proxy;

import java.nio.ByteBuffer;
import java.util.Map;

public interface ProxyHandler {

    String createProxyRequest(String hostName, Map<String, String> additionalHeaders);

    Boolean validateProxyReply(ByteBuffer buffer);

}
