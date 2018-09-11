/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.Proxy;
import org.apache.qpid.proton.engine.impl.TransportImpl;
import org.apache.qpid.proton.engine.impl.TransportInput;
import org.apache.qpid.proton.engine.impl.TransportOutput;
import org.apache.qpid.proton.engine.impl.TransportWrapper;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
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
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
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

    @Test
    public void testProxyHandshakeStatesBeforeConfigure() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();

        Assert.assertFalse(proxyImpl.getIsHandshakeInProgress());

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_NOT_STARTED);
        Assert.assertFalse(proxyImpl.getIsHandshakeInProgress());

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTING);
        Assert.assertFalse(proxyImpl.getIsHandshakeInProgress());

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);
        Assert.assertFalse(proxyImpl.getIsHandshakeInProgress());

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);
        Assert.assertFalse(proxyImpl.getIsHandshakeInProgress());
    }

    @Test
    public void testProxyHandshakeStatesAfterConfigure() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        ProxyHandlerImpl proxyHandler = mock(ProxyHandlerImpl.class);
        TransportImpl transport = mock(TransportImpl.class);
        proxyImpl.configure(hostName, headers, proxyHandler, transport);

        Assert.assertTrue(proxyImpl.getIsHandshakeInProgress());

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTING);
        Assert.assertTrue(proxyImpl.getIsHandshakeInProgress());

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);
        Assert.assertFalse(proxyImpl.getIsHandshakeInProgress());

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);
        Assert.assertFalse(proxyImpl.getIsHandshakeInProgress());
    }

    @Test
    public void testPendingWhenProxyStateIsNotStarted() {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, new ProxyHandlerImpl(), mock(TransportImpl.class));

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());

        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        int bytesCount = transportWrapper.pending();

        Assert.assertEquals(proxyConnectRequestLength, transportWrapper.head().remaining());

        ByteBuffer outputBuffer = proxyImpl.getOutputBuffer();
        outputBuffer.flip();

        Assert.assertEquals(proxyConnectRequestLength, transportWrapper.head().remaining());

        Assert.assertEquals(proxyConnectRequestLength, outputBuffer.remaining());
        Assert.assertEquals(proxyConnectRequestLength, bytesCount);
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());
    }

    @Test
    public void testPendingWhenProxyStateIsNotStartedAndOutputBufferIsNotEmpty() {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, new ProxyHandlerImpl(), mock(TransportImpl.class));

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());

        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        String message = "olddata";
        ByteBuffer outputBuffer = proxyImpl.getOutputBuffer();
        outputBuffer.put(message.getBytes());

        int bytesCount = transportWrapper.pending();

        outputBuffer.flip();
        Assert.assertEquals(message.length(), outputBuffer.remaining());
        Assert.assertEquals(message.length(), bytesCount);
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
    }

    @Test
    public void testPendingWhenProxyStateIsConnecting() {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, new ProxyHandlerImpl(), mock(TransportImpl.class));

        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        transportWrapper.pending();

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());

        for (int i=0; i<10; i++) {
            transportWrapper.pending();
        }

        Assert.assertEquals(proxyConnectRequestLength, transportWrapper.head().remaining());

        ByteBuffer outputBuffer = proxyImpl.getOutputBuffer();
        outputBuffer.flip();

        Assert.assertEquals(proxyConnectRequestLength, outputBuffer.remaining());
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());
    }

    @Test
    public void testPendingWhenProxyStateIsConnected() throws Exception {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, new ProxyHandlerImpl(), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);

        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        transportWrapper.pending();
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTED, proxyImpl.getProxyState());
    }

    @Test
    public void testPendingWhenProxyStateIsFailed() throws Exception {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, new ProxyHandlerImpl(), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);

        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        transportWrapper.pending();
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_FAILED, proxyImpl.getProxyState());
    }

    @Test
    public void testProcessWhenProxyStateNotStarted() throws Exception {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, new ProxyHandlerImpl(), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        transportWrapper.process();
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());

    }

    private void setProxyState(ProxyImpl proxyImpl, Proxy.ProxyState proxyState) throws NoSuchFieldException, IllegalAccessException {
        Field proxyStateField = ProxyImpl.class.getDeclaredField("proxyState");
        proxyStateField.setAccessible(true);
        proxyStateField.set(proxyImpl, proxyState);
        Assert.assertEquals(proxyState, proxyImpl.getProxyState());
    }
}
