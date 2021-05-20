// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.ws.impl;

import com.microsoft.azure.proton.transport.ws.WebSocket;
import com.microsoft.azure.proton.transport.ws.WebSocketHandler;
import com.microsoft.azure.proton.transport.ws.WebSocketHeader;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.TransportException;
import org.apache.qpid.proton.engine.impl.ByteBufferUtils;
import org.apache.qpid.proton.engine.impl.TransportInput;
import org.apache.qpid.proton.engine.impl.TransportOutput;
import org.apache.qpid.proton.engine.impl.TransportWrapper;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WebSocketImplTest {

    private static final int ALLOCATED_WEB_SOCKET_BUFFER_SIZE = (4 * 1024) + (16 * WebSocketHeader.MED_HEADER_LENGTH_MASKED);
    private static final int LENGTH_OF_UPGRADE_REQUEST = 284;

    private String hostName = "host_XXX";
    private String webSocketPath = "path1/path2";
    private String webSocketQuery = "";
    private int webSocketPort = 1234567890;
    private String webSocketProtocol = "subprotocol_name";
    private Map<String, String> additionalHeaders = new HashMap<String, String>();

    private void init() {
        additionalHeaders.put("header1", "content1");
        additionalHeaders.put("header2", "content2");
        additionalHeaders.put("header3", "content3");
    }

    @Test
    public void testConstructor() {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl();

        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        ByteBuffer pingBuffer = webSocketImpl.getPingBuffer();

        assertNotNull(inputBuffer);
        assertNotNull(outputBuffer);
        assertNotNull(pingBuffer);

        assertEquals(inputBuffer.capacity(), ALLOCATED_WEB_SOCKET_BUFFER_SIZE);
        assertEquals(outputBuffer.capacity(), ALLOCATED_WEB_SOCKET_BUFFER_SIZE);
        assertEquals(pingBuffer.capacity(), ALLOCATED_WEB_SOCKET_BUFFER_SIZE);

        assertFalse(webSocketImpl.getEnabled());
    }

    @Test
    public void testConstructorWithCustomBufferSize() {
        init();

        int customBufferSize = 10;
        WebSocketImpl webSocketImpl = new WebSocketImpl(customBufferSize);

        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        ByteBuffer pingBuffer = webSocketImpl.getPingBuffer();

        assertNotNull(inputBuffer);
        assertNotNull(outputBuffer);
        assertNotNull(pingBuffer);

        assertEquals(inputBuffer.capacity(), customBufferSize);
        assertEquals(outputBuffer.capacity(), customBufferSize);
        assertEquals(pingBuffer.capacity(), customBufferSize);

        assertFalse(webSocketImpl.getEnabled());
    }

    @Test
    public void testConfigureHandlerNull() {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl();

        webSocketImpl.configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, null);

        assertNotNull(webSocketImpl.getWebSocketHandler());
        assertTrue(webSocketImpl.getEnabled());
    }

    @Test
    public void testConfigureHandlerNotNull() {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        WebSocketHandler webSocketHandler = new WebSocketHandlerImpl();

        webSocketImpl.configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, webSocketHandler);

        Assert.assertEquals(webSocketHandler, webSocketImpl.getWebSocketHandler());
        assertTrue(webSocketImpl.getEnabled());
    }

    @Test
    public void testWriteUpgradeRequest() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl spyWebSocketHandler = spy(webSocketHandler);

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, spyWebSocketHandler);
        webSocketImpl.writeUpgradeRequest();

        verify(spyWebSocketHandler, times(1))
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders);

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.flip();

        assertEquals(LENGTH_OF_UPGRADE_REQUEST, outputBuffer.remaining());
    }

    @Test
    public void testWritePong() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl spyWebSocketHandler = spy(webSocketHandler);

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, spyWebSocketHandler);

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        ByteBuffer pingBuffer = webSocketImpl.getPingBuffer();

        webSocketImpl.writePong();

        verify(spyWebSocketHandler, times(1)).createPong(pingBuffer, outputBuffer);
    }

    @Test
    public void testWriteClose() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl spyWebSocketHandler = spy(webSocketHandler);

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, spyWebSocketHandler);

        String message = "Message";

        ByteBuffer pingBuffer = webSocketImpl.getPingBuffer();
        pingBuffer.clear();
        pingBuffer.put(message.getBytes());

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();

        webSocketImpl.writeClose();

        assertTrue(Arrays.equals(pingBuffer.array(), outputBuffer.array()));
    }

    @Test
    public void testWrapCreatesSniffer() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl.configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, webSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        assertNotNull(transportWrapper);
    }

    @Test
    public void testWrapBufferEnabled() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        ByteBuffer srcBuffer = ByteBuffer.allocate(50);
        srcBuffer.clear();

        ByteBuffer dstBuffer = ByteBuffer.allocate(50);

        webSocketImpl.isWebSocketEnabled = true;
        webSocketImpl.wrapBuffer(srcBuffer, dstBuffer);

        verify(mockWebSocketHandler, times(1)).wrapBuffer(srcBuffer, dstBuffer);
    }

    @Test
    public void testWrapBufferNotEnabled() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        ByteBuffer srcBuffer = ByteBuffer.allocate(25);
        srcBuffer.clear();
        srcBuffer.put("abcdefghijklmnopqrstvwxyz".getBytes());
        srcBuffer.flip();

        ByteBuffer dstBuffer = ByteBuffer.allocate(25);
        dstBuffer.put("1234567890".getBytes());

        webSocketImpl.isWebSocketEnabled = false;
        webSocketImpl.wrapBuffer(srcBuffer, dstBuffer);

        dstBuffer.flip();
        assertTrue(Arrays.equals(srcBuffer.array(), dstBuffer.array()));
        verify(mockWebSocketHandler, times(0)).wrapBuffer((ByteBuffer) any(), (ByteBuffer) any());
    }

    @Test
    public void testUnwrapBufferEnabled() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        ByteBuffer srcBuffer = ByteBuffer.allocate(50);
        srcBuffer.clear();

        webSocketImpl.isWebSocketEnabled = true;
        webSocketImpl.unwrapBuffer(srcBuffer);

        verify(mockWebSocketHandler, times(1)).unwrapBuffer(srcBuffer);
    }

    @Test
    public void testUnwrapBufferNotEnabled() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        ByteBuffer srcBuffer = ByteBuffer.allocate(25);
        srcBuffer.clear();
        srcBuffer.put("abcdefghijklmnopqrstvwxyz".getBytes());
        srcBuffer.flip();

        webSocketImpl.isWebSocketEnabled = false;

        assertTrue(webSocketImpl.unwrapBuffer(srcBuffer).getType() == WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_UNKNOWN);
        verify(mockWebSocketHandler, times(0)).wrapBuffer((ByteBuffer) any(), (ByteBuffer) any());
    }

    @Test
    public void testPendingStateNotStarted() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl.configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, webSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);
        transportWrapper.pending();

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.flip();

        assertTrue(outputBuffer.remaining() == LENGTH_OF_UPGRADE_REQUEST);
    }

    @Test
    public void testPendingStateNotStartedOutputNotEmpty() {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        webSocketImpl.configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, webSocketHandler);

        String message = "Message";
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.put(message.getBytes());

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        assertTrue(message.length() == transportWrapper.pending());

        ByteBuffer actual = webSocketImpl.getOutputBuffer();
        assertTrue(Arrays.equals(outputBuffer.array(), actual.array()));
    }

    @Test
    public void testPendingStateNotStartedHeadClosed() {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        webSocketImpl.configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, webSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        transportWrapper.close_tail();
        assertTrue(transportWrapper.pending() == Transport.END_OF_STREAM);
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_FAILED);

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.flip();

        assertTrue(outputBuffer.remaining() == LENGTH_OF_UPGRADE_REQUEST);
    }

    @Test
    public void testPendingStateConnecting() {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl spyWebSocketHandler = spy(webSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        webSocketImpl.configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, webSocketHandler);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.flip();

        transportWrapper.pending();
        assertTrue(outputBuffer.remaining() == LENGTH_OF_UPGRADE_REQUEST);
    }

    @Test
    public void testPendingStateConnectingHeadClosedEmptyBuffer() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl.configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, webSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.clear();
        transportWrapper.close_tail();

        assertTrue(transportWrapper.pending() == Transport.END_OF_STREAM);
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_FAILED);
    }

    @Test
    public void testPendingStateFlowEmptyOutput() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn("Request");

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.put(message.getBytes());
        when(mockTransportOutput.pending()).thenReturn(0);

        assertEquals(transportWrapper.pending(), 0);
        verify(mockWebSocketHandler, times(0)).wrapBuffer((ByteBuffer) any(), (ByteBuffer) any());
    }

    @Test
    public void testChunkedConnection() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl.configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, webSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);

        // Get the key that the upgrade verifier will expect
        String request = webSocketHandler.createUpgradeRequest("fakehost", "fakepath", "fakequery", 9999, "fakeprotocol", null);
        String[] lines = request.split("\r\n");
        String extractedKey = null;
        for (String l : lines) {
            if (l.startsWith("Sec-WebSocket-Key: ")) {
                extractedKey = l.substring(19).trim();
                break;
            }
        }
        String expectedKey = null;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            expectedKey = Base64.encodeBase64StringLocal(
                messageDigest.digest((extractedKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes())).trim();
        } catch (NoSuchAlgorithmException e) {
            // can't happen since SHA-1 is a known digest
        }
        // Assemble a response that the upgrade verifier will accept
        byte[] fakeInput = (
            "http/1.1 101 switching protocols\nupgrade websocket\nconnection upgrade\nsec-websocket-protocol fakeprotocol\nsec-websocket-accept "
                + expectedKey).getBytes();

        // Feed the response to the verifier, adding one byte at a time to simulate a response broken into chunks.
        // This test inspired by an issue with the IBM JRE which for some reason returned the service's response in multiple pieces.
        int i = 0;
        ByteBuffer inputBuffer = transportWrapper.tail();
        for (i = 0; i < fakeInput.length - 1; i++) {
            inputBuffer.put(fakeInput[i]);
            transportWrapper.process();
            assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        }
        // Add the last byte and the state should change.
        inputBuffer.put(fakeInput[i]);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);
    }

    @Test
    public void testPendingStateFlowOutputNotEmpty() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn("Request");

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.put(message.getBytes());
        when(mockTransportOutput.pending()).thenReturn(message.length());
        when(mockWebSocketHandler.calculateHeaderSize(message.length())).thenReturn((int) WebSocketHeader.MIN_HEADER_LENGTH_MASKED);

        int expected = message.length() + WebSocketHeader.MIN_HEADER_LENGTH_MASKED;
        int actual = transportWrapper.pending();
        assertEquals(expected, actual);
        verify(mockWebSocketHandler, times(0)).wrapBuffer((ByteBuffer) any(), (ByteBuffer) any());
        verify(mockWebSocketHandler, times(1)).calculateHeaderSize(message.length());
    }

    @Test
    public void testPendingStatePongChangesToFlow() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn("Request");
        when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any()))
            .thenReturn(new WebSocketHandler.WebsocketTuple(7, WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_PING));

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());

        transportWrapper.process();

        ByteBuffer pingBuffer = webSocketImpl.getPingBuffer();

        inputBuffer.flip();
        pingBuffer.flip();
        assertTrue(Arrays.equals(inputBuffer.array(), pingBuffer.array()));
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_PONG);

        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);
    }

    @Test
    public void testPendingStateClosingChangesToClosed() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn("Request");
        when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any()))
            .thenReturn(new WebSocketHandler.WebsocketTuple(7, WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_CLOSE));

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());

        transportWrapper.process();

        ByteBuffer pingBuffer = webSocketImpl.getPingBuffer();

        inputBuffer.flip();
        pingBuffer.flip();
        assertTrue(Arrays.equals(inputBuffer.array(), pingBuffer.array()));
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_CLOSING);

        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CLOSED);
    }

    @Test
    public void testPendingStateClosingHeadClosed() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn("Request");
        when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any()))
            .thenReturn(new WebSocketHandler.WebsocketTuple(7, WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_CLOSE));

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());

        transportWrapper.process();

        ByteBuffer pingBuffer = webSocketImpl.getPingBuffer();

        inputBuffer.flip();
        pingBuffer.flip();
        assertTrue(Arrays.equals(inputBuffer.array(), pingBuffer.array()));
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_CLOSING);

        transportWrapper.close_tail();

        transportWrapper.pending();

        assertTrue(transportWrapper.pending() == Transport.END_OF_STREAM);
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_FAILED);
    }

    @Test
    public void testProcessStateNotStarted() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl.configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, webSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);

        verify(mockTransportInput, times(1)).process();
    }

    @Test
    public void testProcessStateChangesFromConnectingToFlowOnValidReply() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn("Request");

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);
    }

//    @Test
//    public void testProcess_state_flow_repeated_reply()
//    {
//        init();
//
//        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
//        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());
//
//        WebSocketImpl webSocketImpl = new WebSocketImpl();
//          webSocketImpl
//              .configure(_hostName,_webSocketPath,_webSocketQuery,_webSocketPort,_webSocketProtocol,_additionalHeaders,mockWebSocketHandler);
//
//        TransportInput mockTransportInput = mock(TransportInput.class);
//        TransportOutput mockTransportOutput = mock(TransportOutput.class);
//
//        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);
//
//        when(mockWebSocketHandler
//        .validateUpgradeReply((ByteBuffer) any()))
//        .thenReturn(true);
//        when(mockWebSocketHandler
//        .createUpgradeRequest(_hostName, _webSocketPath, _webSocketQuery, _webSocketPort, _webSocketProtocol, _additionalHeaders))
//        .thenReturn("Request");
//
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
//        transportWrapper.pending();
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
//        transportWrapper.process();
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);
//
//        String message = "HTTP ";
//        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
//        inputBuffer.clear();
//        inputBuffer.put(message.getBytes());
//
//        transportWrapper.process();
//        verify(mockWebSocketHandler, times(0)).unwrapBuffer((ByteBuffer) any());
//    }

    @Test
    public void testProcessStateFlowCallsUnderlyingAmqp() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = spy(new TransportInput() {
            ByteBuffer bb = ByteBufferUtils.newWriteableBuffer(4224);

            @Override
            public int capacity() {
                return bb.remaining();
            }

            @Override
            public int position() {
                return bb.position();
            }

            @Override
            public ByteBuffer tail() throws TransportException {
                return bb;
            }

            @Override
            public void process() throws TransportException {
            }

            @Override
            public void close_tail() {
            }
        });
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        doNothing().when(mockTransportInput).process();
        doNothing().when(mockTransportInput).close_tail();

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn("Request");
        when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any()))
            .thenReturn(new WebSocketHandler.WebsocketTuple(7, WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_AMQP));

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());

        transportWrapper.process();
        verify(mockTransportInput, times(1)).process();
    }

    /*Not needed*/
//    @Test
//    public void testProcess_state_flow_calls_underlying_empty()
//    {
//        init();
//
//        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
//        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());
//
//        WebSocketImpl webSocketImpl = new WebSocketImpl();
//        webSocketImpl
//            .configure(_hostName, _webSocketPath, _webSocketQuery, _webSocketPort, _webSocketProtocol, _additionalHeaders, mockWebSocketHandler);
//
//        TransportInput mockTransportInput = mock(TransportInput.class);
//        TransportOutput mockTransportOutput = mock(TransportOutput.class);
//
//        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);
//
//        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
//        when(mockWebSocketHandler
//        .createUpgradeRequest(_hostName, _webSocketPath, _webSocketQuery, _webSocketPort, _webSocketProtocol, _additionalHeaders))
//        .thenReturn("Request");
//        when(mockWebSocketHandler
//        .unwrapBuffer((ByteBuffer) any()))
//        .thenReturn(new WebSocketHandler.WebsocketTuple(7, WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_UNKNOWN));
//
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
//        transportWrapper.pending();
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
//        transportWrapper.process();
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);
//
//        String message = "Message";
//        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
//        inputBuffer.clear();
//        inputBuffer.put(message.getBytes());
//
//        transportWrapper.process();
//        verify(mockTransportInput, times(1)).process();
//    }

//    @Test
//    public void testProcess_state_flow_calls_underlying_invalid()
//    {
//        init();
//
//        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
//        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());
//
//        WebSocketImpl webSocketImpl = new WebSocketImpl();
//        webSocketImpl
//           .configure(_hostName, _webSocketPath, _webSocketQuery, _webSocketPort, _webSocketProtocol, _additionalHeaders, mockWebSocketHandler);
//
//        TransportInput mockTransportInput = mock(TransportInput.class);
//        TransportOutput mockTransportOutput = mock(TransportOutput.class);
//
//        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);
//
//        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
//        when(mockWebSocketHandler
//        .createUpgradeRequest(_hostName, _webSocketPath, _webSocketQuery, _webSocketPort, _webSocketProtocol, _additionalHeaders))
//        .thenReturn("Request");
//        when(mockWebSocketHandler
//        .unwrapBuffer((ByteBuffer) any()))
//        .thenReturn(new WebSocketHandler.WebsocketTuple(7, WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_UNKNOWN));
//
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
//        transportWrapper.pending();
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
//        transportWrapper.process();
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);
//
//        String message = "Message";
//        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
//        inputBuffer.clear();
//        inputBuffer.put(message.getBytes());
//
//        transportWrapper.process();
//        verify(mockTransportInput, times(1)).process();
//    }

    //    @Test
//    public void testProcess_state_flow_calls_underlying_invalid_length()
//    {
//        init();
//
//        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
//        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());
//
//        WebSocketImpl webSocketImpl = new WebSocketImpl();
//        webSocketImpl
//           .configure(_hostName, _webSocketPath, _webSocketQuery, _webSocketPort, _webSocketProtocol, _additionalHeaders, mockWebSocketHandler);
//
//        TransportInput mockTransportInput = mock(TransportInput.class);
//        TransportOutput mockTransportOutput = mock(TransportOutput.class);
//
//        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);
//
//        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
//        when(mockWebSocketHandler
//        .createUpgradeRequest(_hostName, _webSocketPath, _webSocketQuery, _webSocketPort, _webSocketProtocol, _additionalHeaders))
//        .thenReturn("Request");
//        when(mockWebSocketHandler
//        .unwrapBuffer((ByteBuffer) any()))
//        .thenReturn(WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_UNKNOWN);
//
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
//        transportWrapper.pending();
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
//        transportWrapper.process();
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);
//
//        String message = "Message";
//        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
//        inputBuffer.clear();
//        inputBuffer.put(message.getBytes());
//
//        transportWrapper.process();
//        verify(mockTransportInput, times(1)).process();
//    }
//
//    @Test
//    public void testProcess_state_flow_calls_underlying_invalid_masked()
//    {
//        init();
//
//        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
//        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());
//
//        WebSocketImpl webSocketImpl = new WebSocketImpl();
//        webSocketImpl
//           .configure(_hostName, _webSocketPath, _webSocketQuery, _webSocketPort, _webSocketProtocol, _additionalHeaders, mockWebSocketHandler);
//
//        TransportInput mockTransportInput = mock(TransportInput.class);
//        TransportOutput mockTransportOutput = mock(TransportOutput.class);
//
//        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);
//
//        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
//        when(mockWebSocketHandler
//        .createUpgradeRequest(_hostName, _webSocketPath, _webSocketQuery, _webSocketPort, _webSocketProtocol, _additionalHeaders))
//        .thenReturn("Request");
//        when(mockWebSocketHandler
//        .unwrapBuffer((ByteBuffer) any()))
//        .thenReturn(WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_UNKNOWN);
//
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
//        transportWrapper.pending();
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
//        transportWrapper.process();
//        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);
//
//        String message = "Message";
//        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
//        inputBuffer.clear();
//        inputBuffer.put(message.getBytes());
//
//        transportWrapper.process();
//        verify(mockTransportInput, times(1)).process();
//    }
//
    @Test
    public void testProcessStateFlowChangesToPongAfterPingAndCopiesTheBuffer() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn("Request");
        when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any()))
            .thenReturn(new WebSocketHandler.WebsocketTuple(7, WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_PING));

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());

        transportWrapper.process();

        ByteBuffer pingBuffer = webSocketImpl.getPingBuffer();

        inputBuffer.flip();
        pingBuffer.flip();
        assertTrue(Arrays.equals(inputBuffer.array(), pingBuffer.array()));
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_PONG);
    }

    @Test
    public void testProcessStateFlowChangesToClosingAfterCloseAndCopiesTheBuffer() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn("Request");
        when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any()))
            .thenReturn(new WebSocketHandler.WebsocketTuple(7, WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_CLOSE));

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());

        transportWrapper.process();

        ByteBuffer pingBuffer = webSocketImpl.getPingBuffer();

        inputBuffer.flip();
        pingBuffer.flip();
        assertTrue(Arrays.equals(inputBuffer.array(), pingBuffer.array()));
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_CLOSING);
    }

    @Test
    public void testProcessStatePongChangesToFlowHeadClosed() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn("Request");
        when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any()))
            .thenReturn(new WebSocketHandler.WebsocketTuple(7, WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_PING));

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());

        transportWrapper.process();

        ByteBuffer pingBuffer = webSocketImpl.getPingBuffer();

        inputBuffer.flip();
        pingBuffer.flip();
        assertTrue(Arrays.equals(inputBuffer.array(), pingBuffer.array()));
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_PONG);

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.clear();
        transportWrapper.close_tail();

        transportWrapper.pending();
        assertTrue(transportWrapper.pending() == Transport.END_OF_STREAM);
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_FAILED);
    }

    private byte[] createMessage(int size) {
        byte[] data = new byte[size];

        Utils.getSecureRandom().nextBytes(data);

        byte finbit = (byte) (WebSocketHeader.FINBIT_MASK & 0xFF);
        byte opcode = WebSocketHeader.OPCODE_MASK & 0x2;
        byte firstbyte = (byte) (finbit | opcode);
        byte secondbyte = (byte) (size - 2);

        data[0] = firstbyte;
        data[1] = secondbyte;

        return data;
    }

    private byte[] getChunk(byte[] source, int chunkSize, int startPosition) {
        return Arrays.copyOfRange(source, startPosition, Math.min(source.length, startPosition + chunkSize));
    }

    @Test
    public void testProcessMultipleWSRequestsChunks() {
        byte[] message1 = createMessage(100);
        byte[] message2 = createMessage(100);
        byte[] message3 = createMessage(100);

        byte[] concatenatedArray = new byte[message1.length + message2.length + message3.length];
        final byte[] expectedFinalArray = new byte[concatenatedArray.length - 6];
        final ByteBuffer actualFinalBuffer = ByteBufferUtils.newWriteableBuffer(4224);

        System.arraycopy(message1, 0, concatenatedArray, 0, message1.length);
        System.arraycopy(message2, 0, concatenatedArray, message1.length, message2.length);
        System.arraycopy(message3, 0, concatenatedArray, message1.length + message2.length, message3.length);

        System.arraycopy(message1, 2, expectedFinalArray, 0, message1.length - 2);
        System.arraycopy(message2, 2, expectedFinalArray, message1.length - 2, message2.length - 2);
        System.arraycopy(message3, 2, expectedFinalArray, message1.length + message2.length - 4, message3.length - 2);

        int chunkCount = 10;
        int bytesPerChunk = concatenatedArray.length / chunkCount;

        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = spy(new TransportInput() {
            ByteBuffer bb = ByteBufferUtils.newWriteableBuffer(4224);

            @Override
            public int capacity() {
                return bb.remaining();
            }

            @Override
            public int position() {
                return bb.position();
            }

            @Override
            public ByteBuffer tail() throws TransportException {
                return bb;
            }

            @Override
            public void process() throws TransportException {
                bb.flip();
                actualFinalBuffer.put(bb);
            }

            @Override
            public void close_tail() {
            }
        });
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        //doNothing().when(mockTransportInput).process();
        doNothing().when(mockTransportInput).close_tail();
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn("Request");
        when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any())).thenAnswer(new Answer<WebSocketHandler.WebsocketTuple>() {
            @Override
            public WebSocketHandler.WebsocketTuple answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                ByteBuffer bb = (ByteBuffer) arguments[0];
                bb.position(2);
                return new WebSocketHandler.WebsocketTuple(98, WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_AMQP);
            }
        });

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        ByteBuffer wsInputBuffer = webSocketImpl.getWsInputBuffer();

        for (int i = 0; i < chunkCount; i++) {
            byte[] message = getChunk(concatenatedArray, bytesPerChunk, i * bytesPerChunk);
            inputBuffer.clear();
            inputBuffer.put(message);
            transportWrapper.process();
            mockTransportInput.tail().clear();
        }

        actualFinalBuffer.flip();
        byte[] actualFinalArray = new byte[actualFinalBuffer.limit()];
        actualFinalBuffer.get(actualFinalArray);
        assertTrue(Arrays.equals(expectedFinalArray, actualFinalArray));

        //Subtract 1 because the first 2 bytes are used as the header which come in 2 separate chunks
        //verify(mockTransportInput, times(chunkCount-1)).process();
    }

    @Test
    public void testProcessSmallChunksOneBytePayloadNoMask() {
        final int payloadLength = 125;
        int chunkSize = 10;
        int chunkCount = 1 + payloadLength / chunkSize;

        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = spy(new TransportInput() {
            ByteBuffer bb = ByteBufferUtils.newWriteableBuffer(4224);

            @Override
            public int capacity() {
                return bb.remaining();
            }

            @Override
            public int position() {
                return bb.position();
            }

            @Override
            public ByteBuffer tail() throws TransportException {
                return bb;
            }

            @Override
            public void process() throws TransportException {
            }

            @Override
            public void close_tail() {
            }
        });
        TransportOutput mockTransportOutput = mock(TransportOutput.class);
        doNothing().when(mockTransportInput).process();
        doNothing().when(mockTransportInput).close_tail();
        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn("Request");
        when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any())).thenAnswer(new Answer<WebSocketHandler.WebsocketTuple>() {
            @Override
            public WebSocketHandler.WebsocketTuple answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                ByteBuffer bb = (ByteBuffer) arguments[0];
                bb.position(2);
                return new WebSocketHandler.WebsocketTuple(payloadLength, WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_AMQP);
            }
        });

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        ByteBuffer wsInputBuffer = webSocketImpl.getWsInputBuffer();

        byte[] message = new byte[chunkSize];

        byte finbit = (byte) (WebSocketHeader.FINBIT_MASK & 0xFF);
        byte opcode = WebSocketHeader.OPCODE_MASK & 0x2;
        byte firstbyte = (byte) (finbit | opcode);
        byte secondbyte = (byte) payloadLength;

        message[0] = firstbyte;
        message[1] = secondbyte;

        int currentLength = 0;
        for (int i = 0; i < chunkCount + 1; i++) {
            if (i == 0) {
                inputBuffer.clear();
                inputBuffer.put(firstbyte);
                currentLength += 1;
                transportWrapper.process();
            } else if (i == 1) {
                inputBuffer.clear();
                inputBuffer.put(secondbyte);
                currentLength += 1;
                transportWrapper.process();
            } else {
                char c = (char) (65 + i & 0xFF);
                for (int j = 0; j < chunkSize; j++) {
                    message[j] += c;
                }
                inputBuffer.clear();
                inputBuffer.put(message);
                currentLength += chunkSize;
                transportWrapper.process();

                //Get the buffer from the underlying input to check
                ByteBuffer bb = mockTransportInput.tail();
                bb.flip();
                byte[] transportInputArray = new byte[bb.remaining()];
                bb.duplicate().get(transportInputArray);

                //Check that the message chunk we sent is what the underlying input is going to process
                assertTrue(Arrays.equals(message, transportInputArray));
                mockTransportInput.tail().clear();
            }

            assertEquals(inputBuffer.position(), 0);
            assertEquals(inputBuffer.limit(), inputBuffer.capacity());
        }

        //Subtract 1 because the first 2 bytes are used as the header which come in 2 separate chunks
        verify(mockTransportInput, times(chunkCount - 1)).process();
    }

    @Test
    public void testHeadWebsocketNotEnabled() {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl();

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        webSocketImpl.isWebSocketEnabled = false;

        transportWrapper.head();
        verify(mockTransportOutput, times(1)).head();
    }

    @Test
    public void testHeadStateNotStarted() {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl();

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        webSocketImpl.isWebSocketEnabled = true;

        transportWrapper.head();
        verify(mockTransportOutput, times(1)).head();
    }

    @Test
    public void testHeadStateConnecting() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String request = "Request";
        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn(request);
        //when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any())).thenReturn(WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_PING);

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);

        ByteBuffer actual = transportWrapper.head();
        byte[] a = new byte[actual.remaining()];
        actual.get(a);

        assertTrue(Arrays.equals(request.getBytes(), a));
    }

    @Test
    public void testHeadStateFlowUnderlyingHeadEmpty() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String request = "Request";
        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn(request);
        //when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any())).thenReturn(WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_PING);

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        ByteBuffer actual = transportWrapper.head();
        byte[] a = new byte[actual.remaining()];
        actual.get(a);

        assertTrue(Arrays.equals(request.getBytes(), a));
        verify(mockTransportOutput, times(0)).head();
    }

    @Test
    public void testHeadStateFlowUnderlyingHeadNotEmpty() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String request = "Request";
        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn(request);
        //when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any())).thenReturn(WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_PING);

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        when(mockTransportOutput.pending()).thenReturn(1024);

        ByteBuffer actual = transportWrapper.head();
        byte[] a = new byte[actual.remaining()];
        actual.get(a);

        assertTrue(Arrays.equals(request.getBytes(), a));
        verify(mockWebSocketHandler, times(1)).wrapBuffer((ByteBuffer) any(), (ByteBuffer) any());
        verify(mockTransportOutput, times(1)).head();
    }

    @Test
    public void testHeadStatePong() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String request = "Request";
        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn(request);
        when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any()))
            .thenReturn(new WebSocketHandler.WebsocketTuple(7, WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_PING));

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());

        transportWrapper.process();

        ByteBuffer pingBuffer = webSocketImpl.getPingBuffer();

        inputBuffer.flip();
        pingBuffer.flip();
        assertTrue(Arrays.equals(inputBuffer.array(), pingBuffer.array()));
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_PONG);

        ByteBuffer actual = transportWrapper.head();
        byte[] a = new byte[actual.remaining()];
        actual.get(a);

        assertTrue(Arrays.equals(request.getBytes(), a));
    }

    @Test
    public void testPopWebsocketNotEnabled() {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl();

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        webSocketImpl.isWebSocketEnabled = false;

        String message = "Message";
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.clear();
        outputBuffer.put(message.getBytes());

        transportWrapper.pop(message.getBytes().length);

        verify(mockTransportOutput, times(1)).pop(message.getBytes().length);
    }

    @Test
    public void testPopWebsocketNotStarted() {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl();

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        webSocketImpl.isWebSocketEnabled = true;

        String message = "Message";
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.clear();
        outputBuffer.put(message.getBytes());

        transportWrapper.pop(message.getBytes().length);

        verify(mockTransportOutput, times(1)).pop(message.getBytes().length);
    }

    @Test
    public void testPopWebsocketConnecting() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String request = "Request";
        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn(request);
        //when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any())).thenReturn(WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_PING);

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);

        String message = "Message";
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.clear();
        outputBuffer.put(message.getBytes());

        transportWrapper.pop(message.getBytes().length);

        ByteBuffer actual = webSocketImpl.getOutputBuffer();
        assertTrue(actual.limit() == ALLOCATED_WEB_SOCKET_BUFFER_SIZE);
        assertTrue(actual.position() == 0);
    }

    @Test
    public void testPopStateConnectedFlow() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String request = "Request";
        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn(request);
        //when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any())).thenReturn(WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_PING);

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        int webSocketHeaderSize = transportWrapper.pending();

        String message = "Message";
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();

        outputBuffer.clear();
        outputBuffer.put(message.getBytes());

        transportWrapper.pop(message.getBytes().length);

        ByteBuffer actual = webSocketImpl.getOutputBuffer();
        assertTrue(actual.limit() == ALLOCATED_WEB_SOCKET_BUFFER_SIZE);
        assertTrue(actual.position() == 0);

        verify(mockTransportOutput, times(1)).pop(message.getBytes().length - webSocketHeaderSize);
    }

    @Test
    public void testPopStateConnectedPong() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String request = "Request";
        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn(request);
        when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any()))
            .thenReturn(new WebSocketHandler.WebsocketTuple(7, WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_PING));

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());

        transportWrapper.process();

        ByteBuffer pingBuffer = webSocketImpl.getPingBuffer();

        inputBuffer.flip();
        pingBuffer.flip();
        assertTrue(Arrays.equals(inputBuffer.array(), pingBuffer.array()));
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_PONG);

        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();

        transportWrapper.pop(message.getBytes().length);

        ByteBuffer actual = webSocketImpl.getOutputBuffer();
        assertTrue(actual.limit() == ALLOCATED_WEB_SOCKET_BUFFER_SIZE);
        assertTrue(actual.position() == 0);

        verify(mockTransportOutput, times(1)).pop(message.getBytes().length);
    }

    @Test
    public void testPopWebsocketConnectingOutbutBufferIsNotEmpty() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String request = "Request";
        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn(request);
        //when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any())).thenReturn(WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_PING);

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);

        String message = "Message";
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();
        outputBuffer.clear();
        outputBuffer.put(message.getBytes());
        outputBuffer.flip();

        transportWrapper.pop(message.getBytes().length);
        verify(mockTransportOutput, times(1)).pop(message.getBytes().length);
    }

    @Test
    public void testPopStateConnectedFlowOutbutBufferIsNotEmpty() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String request = "Request";
        when(mockWebSocketHandler.validateUpgradeReply((ByteBuffer) any())).thenReturn(true);
        when(mockWebSocketHandler
            .createUpgradeRequest(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders))
            .thenReturn(request);
        //when(mockWebSocketHandler.unwrapBuffer((ByteBuffer) any())).thenReturn(WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_PING);

        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_NOT_STARTED);
        transportWrapper.pending();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTING);
        transportWrapper.process();
        assertTrue(webSocketImpl.getState() == WebSocket.WebSocketState.PN_WS_CONNECTED_FLOW);

        String message = "Message";
        ByteBuffer outputBuffer = webSocketImpl.getOutputBuffer();

        outputBuffer.clear();
        outputBuffer.put(message.getBytes());
        outputBuffer.flip();

        transportWrapper.pop(message.getBytes().length);

        verify(mockTransportOutput, times(1)).pop(message.getBytes().length);
    }

    @Test
    public void testCapacityEnabled() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());
        inputBuffer.flip();

        webSocketImpl.isWebSocketEnabled = true;

        int actual = transportWrapper.capacity();

        assertTrue(message.length() == actual);
        verify(mockTransportInput, times(0)).capacity();
    }

    @Test
    public void testCapacityEnabledTailClosed() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());
        inputBuffer.flip();

        webSocketImpl.isWebSocketEnabled = true;
        transportWrapper.close_tail();

        int actual = transportWrapper.capacity();

        assertTrue(Transport.END_OF_STREAM == actual);
        verify(mockTransportInput, times(0)).capacity();
    }

    @Test
    public void testCapacityNotEnabled() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());
        inputBuffer.flip();

        webSocketImpl.isWebSocketEnabled = false;
        transportWrapper.capacity();

        verify(mockTransportInput, times(1)).capacity();
    }

    @Test
    public void testPositionEnabled() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());

        webSocketImpl.isWebSocketEnabled = true;

        int actual = transportWrapper.position();

        assertTrue(message.length() == actual);
        verify(mockTransportInput, times(0)).position();
    }

    @Test
    public void testPositionEnabledTailClosed() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());

        webSocketImpl.isWebSocketEnabled = true;
        transportWrapper.close_tail();

        int actual = transportWrapper.position();

        assertTrue(Transport.END_OF_STREAM == actual);
        verify(mockTransportInput, times(0)).position();
    }

    @Test
    public void testPositionNotEnabled() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());

        webSocketImpl.isWebSocketEnabled = false;
        transportWrapper.position();

        verify(mockTransportInput, times(1)).position();
    }

    @Test
    public void testTail() {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl();

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        webSocketImpl.isWebSocketEnabled = true;

        String message = "Message";
        ByteBuffer inputBuffer = webSocketImpl.getInputBuffer();
        inputBuffer.clear();
        inputBuffer.put(message.getBytes());
        inputBuffer.flip();

        ByteBuffer actual = transportWrapper.tail();
        byte[] a = new byte[actual.remaining()];
        actual.get(a);

        assertTrue(Arrays.equals(message.getBytes(), a));
        verify(mockTransportInput, times(0)).tail();
    }

    @Test
    public void testTailWebsocketNotEnabled() {
        init();

        WebSocketImpl webSocketImpl = new WebSocketImpl();

        TransportInput mockTransportInput = mock(TransportInput.class);
        TransportOutput mockTransportOutput = mock(TransportOutput.class);

        TransportWrapper transportWrapper = webSocketImpl.wrap(mockTransportInput, mockTransportOutput);

        webSocketImpl.isWebSocketEnabled = false;

        transportWrapper.tail();
        verify(mockTransportInput, times(1)).tail();
    }

    @Test
    public void testToString() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl
            .configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders, mockWebSocketHandler);
        webSocketImpl.isWebSocketEnabled = true;

        String actual = webSocketImpl.toString();

        String expected1 = String.join("", "WebSocketImpl [isWebSocketEnabled=true",
            ", state=PN_WS_NOT_STARTED",
            ", protocol=" + webSocketProtocol,
            ", host=" + hostName,
            ", path=" + webSocketPath,
            ", query=" + webSocketQuery,
            ", port=" + webSocketPort);
        String expected2 = ", additionalHeaders=header3:content3, header2:content2, header1:content1]";

        assertTrue(actual.startsWith(expected1));
        actual = actual.substring(expected1.length());
        assertTrue(actual.equals(expected2));
    }

    @Test
    public void testToStringNoAdditionalHeaders() {
        init();

        WebSocketHandlerImpl webSocketHandler = new WebSocketHandlerImpl();
        WebSocketHandlerImpl mockWebSocketHandler = mock(webSocketHandler.getClass());

        WebSocketImpl webSocketImpl = new WebSocketImpl();
        webSocketImpl.configure(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, null, mockWebSocketHandler);
        webSocketImpl.isWebSocketEnabled = true;

        String actual = webSocketImpl.toString();

        String expected = String.join("", "WebSocketImpl [isWebSocketEnabled=true",
            ", state=PN_WS_NOT_STARTED",
            ", protocol=" + webSocketProtocol,
            ", host=" + hostName,
            ", path=" + webSocketPath,
            ", query=" + webSocketQuery,
            ", port=" + webSocketPort,
            "]");

        assertEquals("Unexpected value for toString()", expected, actual);
    }
}
