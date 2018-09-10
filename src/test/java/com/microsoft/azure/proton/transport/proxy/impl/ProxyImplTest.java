/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy.impl;

import org.apache.qpid.proton.engine.impl.TransportImpl;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

public class ProxyImplTest {

    private String hostName = "test.host.name";
    private int bufferSize = 4 * 1024;
    private Map<String, String> headers = new HashMap<>();
    private int proxyConnectRequestLength = 132;

    private void initHeaders() {
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        headers.put("header3", "value3");
    }

    @Test
    public void testConstructor() {
        ProxyImpl proxyImpl = new ProxyImpl();

        Assert.assertEquals(bufferSize, proxyImpl.getInputBuffer().capacity());
        Assert.assertEquals(bufferSize, proxyImpl.getOutputBuffer().capacity());

        Assert.assertFalse(proxyImpl.getIsProxyConfigured());
    }

    @Test
    public void testConfigure() {
        ProxyImpl proxyImpl = new ProxyImpl();
        ProxyHandlerImpl proxyHandler = mock(ProxyHandlerImpl.class);
        TransportImpl transport = mock(TransportImpl.class);

        proxyImpl.configure(hostName, headers, proxyHandler, transport);

        Assert.assertTrue(proxyImpl.getIsProxyConfigured());
        Assert.assertEquals(proxyHandler, proxyImpl.getProxyHandler());
        Assert.assertEquals(transport, proxyImpl.getUnderlyingTransport());
        Assert.assertEquals(headers, proxyImpl.getProxyRequestHeaders());
    }

    @Test
    public void testWriteProxyRequest() {
        initHeaders();

        ProxyHandlerImpl spyProxyHandler = spy(new ProxyHandlerImpl());
        TransportImpl transport = mock(TransportImpl.class);

        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, spyProxyHandler, transport);
        proxyImpl.writeProxyRequest();

        verify(spyProxyHandler, times(1)).createProxyRequest(hostName, headers);

        ByteBuffer outputBuffer = proxyImpl.getOutputBuffer();
        outputBuffer.flip();

        Assert.assertEquals(proxyConnectRequestLength, outputBuffer.remaining());
    }
}
