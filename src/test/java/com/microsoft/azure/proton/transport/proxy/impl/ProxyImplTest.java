/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.Proxy;
import com.microsoft.azure.proton.transport.proxy.ProxyHandler;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.TransportException;
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

// \org\apache\qpid\proton\reactor\impl\IOHandler.java > connectionReadable and connectionWriteable
// methods are the starting point which invokes all methods of TransportInput and TransportOutput
// classes - to implement transport layering.
// Goal of this class is to test - expected outcomes of proxy transport layer
// when these methods are invoked, and how ProxyState state transitions plays along.
public class ProxyImplTest {

    private String hostName = "test.host.name";
    private int bufferSize = 2 * 1024 * 1024;
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
        Assert.assertTrue(proxyImpl.getIsHandshakeInProgress());
    }

    @Test
    public void testPendingWhenProxyStateIsNotStarted() {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, new ProxyHandlerImpl(), mock(TransportImpl.class));

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());

        TransportOutput mockOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mockOutput);
        int bytesCount = transportWrapper.pending();

        Assert.assertEquals(proxyConnectRequestLength, transportWrapper.head().remaining());

        ByteBuffer outputBuffer = proxyImpl.getOutputBuffer();
        outputBuffer.flip();

        Assert.assertEquals(proxyConnectRequestLength, transportWrapper.head().remaining());

        Assert.assertEquals(proxyConnectRequestLength, outputBuffer.remaining());
        Assert.assertEquals(proxyConnectRequestLength, bytesCount);
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());

        verify(mockOutput, times(0)).pending();
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
        Assert.assertEquals(-1, transportWrapper.pending());
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_FAILED, proxyImpl.getProxyState());
    }

    @Test
    public void testProcessWhenProxyStateNotStarted() {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, new ProxyHandlerImpl(), mock(TransportImpl.class));
        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        transportWrapper.process();
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());

        verify(mockInput, times(1)).process();
    }

    @Test
    public void testProcessWhenProxyStateConnectingTransitionsToConnectedOnValidResponse() {
        ProxyImpl proxyImpl = new ProxyImpl();
        ProxyHandler mockHandler = mock(ProxyHandler.class);
        proxyImpl.configure(hostName, headers, mockHandler, mock(TransportImpl.class));
        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));
        ProxyHandler.ProxyResponseResult mockResponse = mock(ProxyHandler.ProxyResponseResult.class);

        when(mockHandler.createProxyRequest((String) any(), (Map<String, String>) any())).thenReturn("proxy request");

        when(mockResponse.getIsSuccess()).thenReturn(true);
        when(mockResponse.getError()).thenReturn(null);
        when(mockHandler.validateProxyResponse((ByteBuffer) any())).thenReturn(mockResponse);

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        transportWrapper.pending();

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());
        transportWrapper.process();

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTED, proxyImpl.getProxyState());
    }

    @Test
    public void testProcessProxyStateConnectingFailureLeadsToUnderlyingTransportClosed() {
        ProxyImpl proxyImpl = new ProxyImpl();
        ProxyHandler mockHandler = mock(ProxyHandler.class);
        TransportImpl mockTransport = mock(TransportImpl.class);
        proxyImpl.configure(hostName, headers, mockHandler, mockTransport);
        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));
        ProxyHandler.ProxyResponseResult mockResponse = mock(ProxyHandler.ProxyResponseResult.class);

        when(mockHandler.createProxyRequest((String) any(), (Map<String, String>) any())).thenReturn("proxy request");

        when(mockResponse.getIsSuccess()).thenReturn(false);
        when(mockResponse.getError()).thenReturn("proxy failure response");
        when(mockHandler.validateProxyResponse((ByteBuffer) any())).thenReturn(mockResponse);

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        transportWrapper.pending();

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());
        transportWrapper.process();

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());
        verify(mockTransport, times(1)).closed((TransportException) any());
    }

    @Test
    public void testProcessProxyStateIsConnected() throws Exception {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);

        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));
        transportWrapper.process();

        verify(mockInput, times(1)).process();
    }

    @Test
    public void testProcessProxyStateIsFailed() throws Exception {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);

        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));
        transportWrapper.process();

        verify(mockInput, times(1)).process();
    }

    @Test
    public void testPopProxyStateIsNotStarted() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_NOT_STARTED);

        TransportOutput mockOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mockOutput);
        transportWrapper.pop(20);

        verify(mockOutput, times(1)).pop(20);
    }

    @Test
    public void testPopProxyStateConnecting() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTING);

        ByteBuffer outputBuffer = proxyImpl.getOutputBuffer();
        byte[] outputBufferData = "test pop moves position".getBytes();
        outputBuffer.put(outputBufferData);
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        Assert.assertEquals(outputBufferData.length, outputBuffer.position());

        transportWrapper.pop(5);

        Assert.assertEquals(outputBufferData.length - 5, outputBuffer.position());
        Assert.assertEquals(bufferSize - outputBufferData.length + 5, outputBuffer.remaining());

        ByteBuffer head = transportWrapper.head();
        Assert.assertEquals(0, head.position());
        Assert.assertEquals(outputBufferData.length - 5, head.remaining());
    }

    @Test
    public void testPopProxyStateIsConnected() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);

        TransportOutput mockOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mockOutput);
        transportWrapper.pop(20);

        verify(mockOutput, times(1)).pop(20);
    }

    @Test
    public void testPopProxyStateIsFailed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);

        TransportOutput mockOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mockOutput);
        transportWrapper.pop(20);

        verify(mockOutput, times(1)).pop(20);
    }

    @Test
    public void testTailReturnsCurrentInputBufferExceptProxyStateIsConnected() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_NOT_STARTED);
        Assert.assertTrue(proxyImpl.getInputBuffer() == transportWrapper.tail());

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTING);
        Assert.assertTrue(proxyImpl.getInputBuffer() == transportWrapper.tail());

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);
        Assert.assertTrue(proxyImpl.getInputBuffer() == transportWrapper.tail());

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);
        Assert.assertTrue(proxyImpl.getInputBuffer() != transportWrapper.tail());
        verify(mockInput, times(1)).tail();
    }

    @Test
    public void testHeadDelegatesToUnderlyingOutputWhenProxyStateIsConnected() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportOutput mockOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mockOutput);

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);
        transportWrapper.head();
        verify(mockOutput, times(1)).head();
    }

    @Test
    public void testPositionWhenProxyStateIsNotStarted() {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        Assert.assertEquals(0, transportWrapper.position());
    }

    @Test
    public void testPositionWhenProxyStateIsNotStartedAndTailClosed() {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        transportWrapper.close_tail();
        Assert.assertEquals(Transport.END_OF_STREAM, transportWrapper.capacity());
    }

    @Test
    public void testPositionWhenProxyStateIsConnecting() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTING);
        Assert.assertEquals(0, transportWrapper.position());
    }

    @Test
    public void testPositionWhenProxyStateIsConnectingAndTailClosed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTING);
        transportWrapper.close_tail();
        Assert.assertEquals(Transport.END_OF_STREAM, transportWrapper.position());
    }

    @Test
    public void testPositionWhenProxyStateIsConnected() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);
        transportWrapper.position();

        verify(mockInput, times(1)).position();
    }

    @Test
    public void testPositionWhenProxyStateIsConnectedAndTailClosed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);
        transportWrapper.close_tail();
        transportWrapper.position();

        verify(mockInput, times(1)).position();
    }

    @Test
    public void testPositionWhenProxyStateIsFailed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);
        Assert.assertEquals(0, transportWrapper.position());
    }

    @Test
    public void testPositionWhenProxyStateIsFailedAndTailClosed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);
        transportWrapper.close_tail();
        Assert.assertEquals(Transport.END_OF_STREAM, transportWrapper.position());
    }

    @Test
    public void testCapacityWhenProxyStateIsNotStarted() {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        Assert.assertEquals(bufferSize, transportWrapper.capacity());
    }

    @Test
    public void testCapacityWhenProxyStateIsNotStartedAndTailClosed() {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        transportWrapper.close_tail();
        Assert.assertEquals(Transport.END_OF_STREAM, transportWrapper.capacity());
    }

    @Test
    public void testCapacityWhenProxyStateIsConnecting() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTING);
        Assert.assertEquals(bufferSize, transportWrapper.capacity());
    }

    @Test
    public void testCapacityWhenProxyStateIsConnectingAndTailClosed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTING);
        transportWrapper.close_tail();
        Assert.assertEquals(Transport.END_OF_STREAM, transportWrapper.capacity());
    }

    @Test
    public void testCapacityWhenProxyStateIsConnected() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);
        transportWrapper.capacity();

        verify(mockInput, times(1)).capacity();
    }

    @Test
    public void testCapacityWhenProxyStateIsConnectedAndTailClosed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);
        transportWrapper.close_tail();
        transportWrapper.capacity();

        verify(mockInput, times(1)).capacity();
    }

    @Test
    public void testCapacityWhenProxyStateIsFailed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);
        Assert.assertEquals(bufferSize, transportWrapper.capacity());
    }

    @Test
    public void testCapacityWhenProxyStateIsFailedAndTailClosed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(hostName, headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);
        transportWrapper.close_tail();
        Assert.assertEquals(Transport.END_OF_STREAM, transportWrapper.capacity());
    }

    private void setProxyState(ProxyImpl proxyImpl, Proxy.ProxyState proxyState) throws NoSuchFieldException, IllegalAccessException {
        Field proxyStateField = ProxyImpl.class.getDeclaredField("proxyState");
        proxyStateField.setAccessible(true);
        proxyStateField.set(proxyImpl, proxyState);
        Assert.assertEquals(proxyState, proxyImpl.getProxyState());
    }
}
