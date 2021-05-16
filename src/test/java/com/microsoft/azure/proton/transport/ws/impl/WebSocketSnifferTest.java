// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.ws.impl;

import com.microsoft.azure.proton.transport.ws.WebSocketHeader;
import org.apache.qpid.proton.engine.impl.TransportWrapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class WebSocketSnifferTest {
    @Test
    public void testMakeDeterminationWrapper1() {
        TransportWrapper mockTransportWrapper1 = mock(TransportWrapper.class);
        TransportWrapper mockTransportWrapper2 = mock(TransportWrapper.class);

        WebSocketSniffer webSocketSniffer = new WebSocketSniffer(mockTransportWrapper1, mockTransportWrapper2);

        assertEquals("Incorrect header size", WebSocketHeader.MIN_HEADER_LENGTH_MASKED, webSocketSniffer.bufferSize());

        byte[] bytes = new byte[WebSocketHeader.MIN_HEADER_LENGTH_MASKED];
        bytes[0] = WebSocketHeader.FINAL_OPCODE_BINARY;

        webSocketSniffer.makeDetermination(bytes);
        assertEquals("Incorrect wrapper selected", mockTransportWrapper1, webSocketSniffer.getSelectedTransportWrapper());
    }

    @Test
    public void testMakeDeterminationWrapper2() {
        TransportWrapper mockTransportWrapper1 = mock(TransportWrapper.class);
        TransportWrapper mockTransportWrapper2 = mock(TransportWrapper.class);

        WebSocketSniffer webSocketSniffer = new WebSocketSniffer(mockTransportWrapper1, mockTransportWrapper2);

        assertEquals("Incorrect header size", WebSocketHeader.MIN_HEADER_LENGTH_MASKED, webSocketSniffer.bufferSize());

        byte[] bytes = new byte[WebSocketHeader.MIN_HEADER_LENGTH_MASKED];
        bytes[0] = (byte) 0x81;

        webSocketSniffer.makeDetermination(bytes);
        assertEquals("Incorrect wrapper selected", mockTransportWrapper2, webSocketSniffer.getSelectedTransportWrapper());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMakeDeterminationInsufficientBytes() {
        TransportWrapper mockTransportWrapper1 = mock(TransportWrapper.class);
        TransportWrapper mockTransportWrapper2 = mock(TransportWrapper.class);

        WebSocketSniffer webSocketSniffer = new WebSocketSniffer(mockTransportWrapper1, mockTransportWrapper2);

        assertEquals("Incorrect header size", WebSocketHeader.MIN_HEADER_LENGTH_MASKED, webSocketSniffer.bufferSize());

        byte[] bytes = new byte[WebSocketHeader.MIN_HEADER_LENGTH_MASKED - 1];
        bytes[0] = WebSocketHeader.FINAL_OPCODE_BINARY;

        webSocketSniffer.makeDetermination(bytes);
    }
}
