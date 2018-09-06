/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */
package com.microsoft.azure.proton.transport.proxy;

import java.nio.ByteBuffer;

public interface ProxyHandler {

    String createProxyRequest(String hostName);

    Boolean validateProxyReply(ByteBuffer buffer);

}
