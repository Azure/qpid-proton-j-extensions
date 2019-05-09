/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.Proxy;
import com.microsoft.azure.proton.transport.proxy.ProxyAuthenticationType;
import com.microsoft.azure.proton.transport.proxy.ProxyConfiguration;
import com.microsoft.azure.proton.transport.proxy.ProxyHandler;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.TransportException;
import org.apache.qpid.proton.engine.impl.TransportImpl;
import org.apache.qpid.proton.engine.impl.TransportInput;
import org.apache.qpid.proton.engine.impl.TransportOutput;
import org.apache.qpid.proton.engine.impl.TransportWrapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.microsoft.azure.proton.transport.proxy.impl.Constants.BASIC;
import static com.microsoft.azure.proton.transport.proxy.impl.Constants.DIGEST;
import static com.microsoft.azure.proton.transport.proxy.impl.Constants.PROXY_AUTHENTICATE_HEADER;
import static com.microsoft.azure.proton.transport.proxy.impl.Constants.PROXY_AUTHORIZATION;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// \org\apache\qpid\proton\reactor\impl\IOHandler.java > connectionReadable and connectionWriteable
// methods are the starting point which invokes all methods of TransportInput and TransportOutput
// classes - to implement transport layering.
// Goal of this class is to test - expected outcomes of proxy transport layer
// when these methods are invoked, and how ProxyState state transitions plays along.

public class ProxyImplTest {
    private static final InetSocketAddress PROXY_ADDRESS = InetSocketAddress.createUnresolved("my.host.name", 8888);
    private static final java.net.Proxy PROXY = new java.net.Proxy(java.net.Proxy.Type.HTTP, PROXY_ADDRESS);
    private static final int BUFFER_SIZE = 8 * 1024;
    private static final String USERNAME = "test-user";
    private static final String PASSWORD = "test-password!";

    private final Logger logger = LoggerFactory.getLogger(ProxyImplTest.class);
    private Map<String, String> headers = new HashMap<>();
    private ProxySelector originalProxy;

    private void initHeaders() {
        headers.put("header1", "value1");
        headers.put("header2", "value2");
        headers.put("header3", "value3");
    }

    @Before
    public void setup() {
        originalProxy = ProxySelector.getDefault();

        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<java.net.Proxy> select(URI uri) {
                List<java.net.Proxy> proxies = new ArrayList<>();
                proxies.add(PROXY);
                return proxies;
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                if (logger.isErrorEnabled()) {
                    logger.error("PROXY CONNECTION FAILED: URI = {}, Socket Address = {}, IO Exception = {}",
                            uri.toString(), sa.toString(), ioe.toString());
                }
            }
        });

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == Authenticator.RequestorType.PROXY)
                    return new PasswordAuthentication(USERNAME, PASSWORD.toCharArray());
                return super.getPasswordAuthentication();
            }
        });
    }

    @After
    public void teardown() {
        ProxySelector.setDefault(originalProxy);
    }

    @Test
    public void testConstructor() {
        ProxyImpl proxyImpl = new ProxyImpl();

        Assert.assertEquals(BUFFER_SIZE, proxyImpl.getInputBuffer().capacity());
        Assert.assertEquals(BUFFER_SIZE, proxyImpl.getOutputBuffer().capacity());

        Assert.assertFalse(proxyImpl.getIsProxyConfigured());
    }

    @Test
    public void testConstructorOverload() {
        final ProxyConfiguration configuration = new ProxyConfiguration(ProxyAuthenticationType.DIGEST, PROXY, USERNAME, PASSWORD);
        final ProxyImpl proxyImpl = new ProxyImpl(configuration);

        Assert.assertEquals(BUFFER_SIZE, proxyImpl.getInputBuffer().capacity());
        Assert.assertEquals(BUFFER_SIZE, proxyImpl.getOutputBuffer().capacity());

        Assert.assertFalse(proxyImpl.getIsProxyConfigured());
    }

    @Test
    public void testConfigure() {
        ProxyImpl proxyImpl = new ProxyImpl();
        ProxyHandlerImpl proxyHandler = mock(ProxyHandlerImpl.class);
        TransportImpl transport = mock(TransportImpl.class);

        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, proxyHandler, transport);

        Assert.assertTrue(proxyImpl.getIsProxyConfigured());
        Assert.assertEquals(proxyHandler, proxyImpl.getProxyHandler());
        Assert.assertEquals(transport, proxyImpl.getUnderlyingTransport());
        Assert.assertEquals(headers, proxyImpl.getProxyRequestHeaders());
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
    }

    @Test
    public void testWriteProxyRequest() {
        initHeaders();
        int expectedRequestLength = getConnectRequestLength(PROXY_ADDRESS.getHostName(), headers);

        ProxyHandlerImpl spyProxyHandler = spy(new ProxyHandlerImpl());
        TransportImpl transport = mock(TransportImpl.class);

        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, spyProxyHandler, transport);
        proxyImpl.writeProxyRequest();

        verify(spyProxyHandler, times(1)).createProxyRequest(PROXY_ADDRESS.getHostName(), headers);

        ByteBuffer outputBuffer = proxyImpl.getOutputBuffer();
        outputBuffer.flip();

        Assert.assertEquals(expectedRequestLength, outputBuffer.remaining());
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
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, proxyHandler, transport);

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
        int expectedRequestLength = getConnectRequestLength(PROXY_ADDRESS.getHostName(), headers);

        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, new ProxyHandlerImpl(), mock(TransportImpl.class));

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());

        TransportOutput mockOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mockOutput);
        int bytesCount = transportWrapper.pending();

        Assert.assertEquals(expectedRequestLength, transportWrapper.head().remaining());

        ByteBuffer outputBuffer = proxyImpl.getOutputBuffer();
        outputBuffer.flip();

        Assert.assertEquals(expectedRequestLength, transportWrapper.head().remaining());

        Assert.assertEquals(expectedRequestLength, outputBuffer.remaining());
        Assert.assertEquals(expectedRequestLength, bytesCount);
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());

        verify(mockOutput, times(0)).pending();
    }

    @Test
    public void testPendingWhenProxyStateIsNotStartedAndOutputBufferIsNotEmpty() {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, new ProxyHandlerImpl(), mock(TransportImpl.class));

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
        int expectedRequestLength = getConnectRequestLength(PROXY_ADDRESS.getHostName(), headers);

        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, new ProxyHandlerImpl(), mock(TransportImpl.class));

        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        transportWrapper.pending();

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());

        for (int i = 0; i < 10; i++) {
            transportWrapper.pending();
        }

        Assert.assertEquals(expectedRequestLength, transportWrapper.head().remaining());

        ByteBuffer outputBuffer = proxyImpl.getOutputBuffer();
        outputBuffer.flip();

        Assert.assertEquals(expectedRequestLength, outputBuffer.remaining());
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());
    }

    @Test
    public void testPendingWhenProxyStateIsConnected() throws Exception {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, new ProxyHandlerImpl(), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);

        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        transportWrapper.pending();
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTED, proxyImpl.getProxyState());
    }

    @Test
    public void testPendingWhenProxyStateIsFailed() throws Exception {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, new ProxyHandlerImpl(), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);

        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        Assert.assertEquals(-1, transportWrapper.pending());
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_FAILED, proxyImpl.getProxyState());
    }

    @Test
    public void testProcessWhenProxyStateNotStarted() {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, new ProxyHandlerImpl(), mock(TransportImpl.class));
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
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mockHandler, mock(TransportImpl.class));
        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));
        ProxyHandler.ProxyResponseResult mockResponse = mock(ProxyHandler.ProxyResponseResult.class);

        when(mockHandler.createProxyRequest(any(), any())).thenReturn("proxy request");

        when(mockResponse.getIsSuccess()).thenReturn(true);
        when(mockResponse.getError()).thenReturn(null);
        when(mockHandler.validateProxyResponse(any())).thenReturn(mockResponse);

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
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mockHandler, mockTransport);
        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));
        ProxyHandler.ProxyResponseResult mockResponse = mock(ProxyHandler.ProxyResponseResult.class);

        when(mockHandler.createProxyRequest(any(), any())).thenReturn("proxy request");

        when(mockResponse.getIsSuccess()).thenReturn(false);
        when(mockResponse.getError()).thenReturn("proxy failure response");
        when(mockHandler.validateProxyResponse(any())).thenReturn(mockResponse);

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        transportWrapper.pending();

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());
        transportWrapper.process();

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());
        verify(mockTransport, times(1)).closed(isA(TransportException.class));
    }

    @Test
    public void testProcessProxyStateIsConnected() throws Exception {
        initHeaders();
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
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
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);

        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));
        transportWrapper.process();

        verify(mockInput, times(1)).process();
    }

    @Test
    public void testPopProxyStateIsNotStarted() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_NOT_STARTED);

        TransportOutput mockOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mockOutput);
        transportWrapper.pop(20);

        verify(mockOutput, times(1)).pop(20);
    }

    @Test
    public void testPopProxyStateConnecting() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTING);

        ByteBuffer outputBuffer = proxyImpl.getOutputBuffer();
        byte[] outputBufferData = "test pop moves position".getBytes();
        outputBuffer.put(outputBufferData);
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        Assert.assertEquals(outputBufferData.length, outputBuffer.position());

        transportWrapper.pop(5);

        Assert.assertEquals(outputBufferData.length - 5, outputBuffer.position());
        Assert.assertEquals(BUFFER_SIZE - outputBufferData.length + 5, outputBuffer.remaining());

        ByteBuffer head = transportWrapper.head();
        Assert.assertEquals(0, head.position());
        Assert.assertEquals(outputBufferData.length - 5, head.remaining());
    }

    @Test
    public void testPopProxyStateIsConnected() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);

        TransportOutput mockOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mockOutput);
        transportWrapper.pop(20);

        verify(mockOutput, times(1)).pop(20);
    }

    @Test
    public void testPopProxyStateIsFailed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);

        TransportOutput mockOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mockOutput);
        transportWrapper.pop(20);

        verify(mockOutput, times(1)).pop(20);
    }

    @Test
    public void testTailReturnsCurrentInputBufferExceptProxyStateIsConnected() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_NOT_STARTED);
        Assert.assertSame(proxyImpl.getInputBuffer(), transportWrapper.tail());

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTING);
        Assert.assertSame(proxyImpl.getInputBuffer(), transportWrapper.tail());

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);
        Assert.assertSame(proxyImpl.getInputBuffer(), transportWrapper.tail());

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);
        Assert.assertNotSame(proxyImpl.getInputBuffer(), transportWrapper.tail());
        verify(mockInput, times(1)).tail();
    }

    @Test
    public void testHeadDelegatesToUnderlyingOutputWhenProxyStateIsConnected() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportOutput mockOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mockOutput);

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);
        transportWrapper.head();
        verify(mockOutput, times(1)).head();
    }

    @Test
    public void testPositionWhenProxyStateIsNotStarted() {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        Assert.assertEquals(0, transportWrapper.position());
    }

    @Test
    public void testPositionWhenProxyStateIsNotStartedAndTailClosed() {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        transportWrapper.close_tail();
        Assert.assertEquals(Transport.END_OF_STREAM, transportWrapper.capacity());
    }

    @Test
    public void testPositionWhenProxyStateIsConnecting() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTING);
        Assert.assertEquals(0, transportWrapper.position());
    }

    @Test
    public void testPositionWhenProxyStateIsConnectingAndTailClosed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTING);
        transportWrapper.close_tail();
        Assert.assertEquals(Transport.END_OF_STREAM, transportWrapper.position());
    }

    @Test
    public void testPositionWhenProxyStateIsConnected() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);
        transportWrapper.position();

        verify(mockInput, times(1)).position();
    }

    @Test
    public void testPositionWhenProxyStateIsConnectedAndTailClosed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
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
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);
        Assert.assertEquals(0, transportWrapper.position());
    }

    @Test
    public void testPositionWhenProxyStateIsFailedAndTailClosed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);
        transportWrapper.close_tail();
        Assert.assertEquals(Transport.END_OF_STREAM, transportWrapper.position());
    }

    @Test
    public void testCapacityWhenProxyStateIsNotStarted() {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        Assert.assertEquals(BUFFER_SIZE, transportWrapper.capacity());
    }

    @Test
    public void testCapacityWhenProxyStateIsNotStartedAndTailClosed() {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        transportWrapper.close_tail();
        Assert.assertEquals(Transport.END_OF_STREAM, transportWrapper.capacity());
    }

    @Test
    public void testCapacityWhenProxyStateIsConnecting() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTING);
        Assert.assertEquals(BUFFER_SIZE, transportWrapper.capacity());
    }

    @Test
    public void testCapacityWhenProxyStateIsConnectingAndTailClosed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTING);
        transportWrapper.close_tail();
        Assert.assertEquals(Transport.END_OF_STREAM, transportWrapper.capacity());
    }

    @Test
    public void testCapacityWhenProxyStateIsConnected() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportInput mockInput = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(mockInput, mock(TransportOutput.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_CONNECTED);
        transportWrapper.capacity();

        verify(mockInput, times(1)).capacity();
    }

    @Test
    public void testCapacityWhenProxyStateIsConnectedAndTailClosed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
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
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));
        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);
        Assert.assertEquals(BUFFER_SIZE, transportWrapper.capacity());
    }

    @Test
    public void testCapacityWhenProxyStateIsFailedAndTailClosed() throws Exception {
        ProxyImpl proxyImpl = new ProxyImpl();
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, mock(ProxyHandler.class), mock(TransportImpl.class));
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), mock(TransportOutput.class));

        setProxyState(proxyImpl, Proxy.ProxyState.PN_PROXY_FAILED);
        transportWrapper.close_tail();
        Assert.assertEquals(Transport.END_OF_STREAM, transportWrapper.capacity());
    }

    /**
     * Verifies that if we explicitly set ProxyAuthenticationType.NONE and the proxy asks for verification then we fail.
     * This also covers the case where the proxy configuration suggests one auth method, but it is not supported in the
     * proxy challenge.
     */
    @Test
    public void authenticationTypeNoneClosesTail() {
        // Arrange
        ProxyConfiguration configuration = new ProxyConfiguration(ProxyAuthenticationType.NONE, PROXY, USERNAME, PASSWORD);
        ProxyImpl proxyImpl = new ProxyImpl(configuration);
        ProxyHandler handler = mock(ProxyHandler.class);
        TransportImpl underlyingTransport = mock(TransportImpl.class);
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, handler, underlyingTransport);
        TransportInput input = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(input, mock(TransportOutput.class));
        ProxyHandler.ProxyResponseResult mockResponse = mock(ProxyHandler.ProxyResponseResult.class);

        when(handler.createProxyRequest(any(), any())).thenReturn("proxy request");
        when(handler.validateProxyResponse(any())).thenReturn(mockResponse);

        when(mockResponse.getIsSuccess()).thenReturn(false);
        when(mockResponse.getError()).thenReturn(getProxyChallenge(true, false));

        // Act and Assert
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        transportWrapper.pending();

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());
        transportWrapper.process();

        verify(underlyingTransport, times(1)).closed(isA(TransportException.class));
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());
    }

    /**
     * Verifies that if we explicitly set ProxyAuthenticationType.NONE and the proxy asks for verification then we fail.
     * This also covers the case where the proxy configuration suggests one auth method, but it is not supported in the
     * proxy challenge.
     */
    @Test
    public void authenticationNoAuthMismatchClosesTail() {
        // Arrange
        ProxyConfiguration configuration = new ProxyConfiguration(ProxyAuthenticationType.BASIC, PROXY, USERNAME, PASSWORD);
        ProxyImpl proxyImpl = new ProxyImpl(configuration);
        ProxyHandler handler = mock(ProxyHandler.class);
        TransportImpl underlyingTransport = mock(TransportImpl.class);
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, handler, underlyingTransport);
        TransportInput input = mock(TransportInput.class);
        TransportWrapper transportWrapper = proxyImpl.wrap(input, mock(TransportOutput.class));
        ProxyHandler.ProxyResponseResult mockResponse = mock(ProxyHandler.ProxyResponseResult.class);

        when(handler.createProxyRequest(any(), any())).thenReturn("proxy request");
        when(handler.validateProxyResponse(any())).thenReturn(mockResponse);

        when(mockResponse.getIsSuccess()).thenReturn(true);
        when(mockResponse.getError()).thenReturn(null);

        // Act and Assert
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        transportWrapper.pending();

        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());
        transportWrapper.process();

        verify(underlyingTransport, times(1)).closed(isA(TransportException.class));
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());
    }

    /**
     * Verifies that we can pass in a proxy configuration and connect to the proxy when the challenge contains the
     * configured auth method.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void authenticationWithProxyConfiguration() {
        // Arrange
        ProxyConfiguration configuration = new ProxyConfiguration(ProxyAuthenticationType.BASIC, PROXY, USERNAME, PASSWORD);
        ProxyImpl proxyImpl = new ProxyImpl(configuration);
        ProxyHandler handler = mock(ProxyHandler.class);
        TransportImpl underlyingTransport = mock(TransportImpl.class);
        TransportOutput output = mock(TransportOutput.class);
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, handler, underlyingTransport);
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), output);
        ProxyHandler.ProxyResponseResult mockResponse = mock(ProxyHandler.ProxyResponseResult.class);

        when(handler.createProxyRequest(any(), any())).thenReturn("proxy request", "proxy request2");
        when(handler.validateProxyResponse(any())).thenReturn(mockResponse);

        when(mockResponse.getIsSuccess()).thenReturn(false, true);
        when(mockResponse.getError()).thenReturn(getProxyChallenge(true, true), "Success.");

        // Act and Assert
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        transportWrapper.pending();

        Assert.assertTrue(proxyImpl.getIsHandshakeInProgress());
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());
        transportWrapper.process();

        // At this point, we've gotten the correct challenger and set the header we want to respond with. We want to
        // zero out the output buffer so that it'll write the headers when getting the request from the proxy handler.
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CHALLENGE, proxyImpl.getProxyState());
        clearOutputBuffer(proxyImpl);
        transportWrapper.pending();

        Assert.assertTrue(proxyImpl.getIsHandshakeInProgress());
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CHALLENGE_RESPONDED, proxyImpl.getProxyState());
        transportWrapper.process();

        Assert.assertFalse(proxyImpl.getIsHandshakeInProgress());
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTED, proxyImpl.getProxyState());

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(handler, times(2)).createProxyRequest(
                argThat(string -> string != null && string.equals(PROXY_ADDRESS.getHostName())), (Map<String, String>) captor.capture());

        boolean foundHeader = false;
        for (Map map : captor.getAllValues()) {
            if (!map.containsKey(PROXY_AUTHORIZATION)) {
                continue;
            }

            String value = (String) map.get(PROXY_AUTHORIZATION);
            if (value.trim().startsWith(BASIC)) {
                foundHeader = true;
                break;
            }
        }

        Assert.assertTrue(foundHeader);
    }

    /**
     * Verifies that when we use the system defaults and both are offered, then we will use the the DIGEST.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void authenticationWithSystemDefaults() {
        // Arrange
        ProxyImpl proxyImpl = new ProxyImpl();
        ProxyHandler handler = mock(ProxyHandler.class);
        TransportImpl underlyingTransport = mock(TransportImpl.class);
        TransportOutput output = mock(TransportOutput.class);
        proxyImpl.configure(PROXY_ADDRESS.getHostName(), headers, handler, underlyingTransport);
        TransportWrapper transportWrapper = proxyImpl.wrap(mock(TransportInput.class), output);
        ProxyHandler.ProxyResponseResult mockResponse = mock(ProxyHandler.ProxyResponseResult.class);

        when(handler.createProxyRequest(any(), any())).thenReturn("proxy request", "proxy request2");
        when(handler.validateProxyResponse(any())).thenReturn(mockResponse);

        when(mockResponse.getIsSuccess()).thenReturn(false, true);
        when(mockResponse.getError()).thenReturn(getProxyChallenge(true, true), "Success.");

        // Act and Assert
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_NOT_STARTED, proxyImpl.getProxyState());
        transportWrapper.pending();

        Assert.assertTrue(proxyImpl.getIsHandshakeInProgress());
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTING, proxyImpl.getProxyState());
        transportWrapper.process();

        // At this point, we've gotten the correct challenger and set the header we want to respond with. We want to
        // zero out the output buffer so that it'll write the headers when getting the request from the proxy handler.
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CHALLENGE, proxyImpl.getProxyState());
        clearOutputBuffer(proxyImpl);
        transportWrapper.pending();

        Assert.assertTrue(proxyImpl.getIsHandshakeInProgress());
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CHALLENGE_RESPONDED, proxyImpl.getProxyState());
        transportWrapper.process();

        Assert.assertFalse(proxyImpl.getIsHandshakeInProgress());
        Assert.assertEquals(Proxy.ProxyState.PN_PROXY_CONNECTED, proxyImpl.getProxyState());

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(handler, times(2)).createProxyRequest(
                argThat(string -> string != null && string.equals(PROXY_ADDRESS.getHostName())), (Map<String, String>) captor.capture());

        boolean foundHeader = false;
        for (Map map : captor.getAllValues()) {
            if (!map.containsKey(PROXY_AUTHORIZATION)) {
                continue;
            }

            String value = (String) map.get(PROXY_AUTHORIZATION);
            if (value.trim().startsWith(DIGEST)) {
                foundHeader = true;
                break;
            }
        }

        Assert.assertTrue(foundHeader);
    }

    private static int getConnectRequestLength(String host, Map<String, String> headers) {
        StringBuilder builder = new StringBuilder(String.format(Locale.ROOT, ProxyHandlerImpl.CONNECT_REQUEST, host, ProxyHandlerImpl.NEW_LINE));

        if (headers != null) {
            headers.forEach((key, value) -> {
                builder.append(String.format(Locale.ROOT, ProxyHandlerImpl.HEADER_FORMAT, key, value));
                builder.append(ProxyHandlerImpl.NEW_LINE);
            });
        }

        builder.append(ProxyHandlerImpl.NEW_LINE);

        return builder.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private void setProxyState(ProxyImpl proxyImpl, Proxy.ProxyState proxyState) throws NoSuchFieldException, IllegalAccessException {
        Field proxyStateField = ProxyImpl.class.getDeclaredField("proxyState");
        proxyStateField.setAccessible(true);
        proxyStateField.set(proxyImpl, proxyState);
        Assert.assertEquals(proxyState, proxyImpl.getProxyState());
    }

    private void clearOutputBuffer(ProxyImpl proxyImpl) {
        // Clears the "ProxyImpl.outputBuffer" field.
        final String outputBufferName = "outputBuffer";

        Field outputBuffer;
        ByteBuffer buffer;
        try {
            outputBuffer = ProxyImpl.class.getDeclaredField(outputBufferName);

            outputBuffer.setAccessible(true);
            buffer = (ByteBuffer) outputBuffer.get(proxyImpl);
            buffer.clear();
        } catch (NoSuchFieldException e) {
            if (logger.isErrorEnabled()) {
                logger.error("Could not locate field '{}' on ProxyImpl class. Exception: {}", outputBufferName, e);
            }
        } catch (IllegalAccessException e) {
            if (logger.isErrorEnabled()) {
                logger.error("Could not fetch byte buffer from object.", e);
            }
        }
    }

    private static String getProxyChallenge(boolean includeBasic, boolean includeDigest) {
        final String newLine = "\n";
        StringBuilder builder = new StringBuilder("HTTP/1.1 407 Proxy Authentication Required");
        builder.append(newLine);
        builder.append("Date: Sun, 05 May 2019 07:28:00 GMT");
        builder.append(newLine);

        if (includeBasic) {
            builder.append(String.join(" ", PROXY_AUTHENTICATE_HEADER, BASIC));
            builder.append(newLine);
        }

        if (includeDigest) {
            builder.append(String.format("%s %s realm=\"%s\", nonce=\"A randomly set nonce.\", qop=\"auth\", stale=false",
                    PROXY_AUTHENTICATE_HEADER, DIGEST, PROXY));
            builder.append(newLine);
        }

        builder.append(newLine);

        return builder.toString();
    }
}
