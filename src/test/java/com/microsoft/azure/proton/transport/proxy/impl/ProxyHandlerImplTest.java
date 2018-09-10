/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy.impl;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class ProxyHandlerImplTest {
    @Test
    public void testCreateProxyRequest() {
        final String hostName = "testHostName";
        final HashMap<String, String> headers = new HashMap<>();
        headers.put("header1", "headervalue1");
        headers.put("header2", "headervalue2");

        ProxyHandlerImpl proxyHandler = new ProxyHandlerImpl();
        final String actualProxyRequest = proxyHandler.createProxyRequest(hostName, headers);

        final String expectedProxyRequest = "CONNECT testHostName HTTP/1.1\r\n" +
                "Host: testHostName\r\n" +
                "Connection: Keep-Alive\r\n" +
                "header2: headervalue2\r\n" +
                "header1: headervalue1\r\n" +
                "\r\n";

        Assert.assertEquals(expectedProxyRequest, actualProxyRequest);
    }
}
