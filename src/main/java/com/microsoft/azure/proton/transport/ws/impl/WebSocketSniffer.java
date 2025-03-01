// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.ws.impl;

import com.microsoft.azure.proton.transport.ws.WebSocketHeader;
import org.apache.qpid.proton.engine.impl.HandshakeSniffingTransportWrapper;
import org.apache.qpid.proton.engine.impl.TransportWrapper;

/**
 * Determines which transport layer to read web socket bytes from.
 */
public class WebSocketSniffer extends HandshakeSniffingTransportWrapper<TransportWrapper, TransportWrapper> {
    /**
     * Creates an instance.
     *
     * @param webSocket Web socket transport layer.
     * @param other The next transport layer.
     */
    public WebSocketSniffer(TransportWrapper webSocket, TransportWrapper other) {
        super(webSocket, other);
    }

    /**
     * Gets the layer in the proton-j transport chain to read web socket frames from.
     *
     * @return The layer in the proton-j transport chain to read web socket frames from.
     */
    protected TransportWrapper getSelectedTransportWrapper() {
        return _selectedTransportWrapper;
    }

    @Override
    protected int bufferSize() {
        return WebSocketHeader.MIN_HEADER_LENGTH_MASKED;
    }

    @Override
    protected void makeDetermination(byte[] bytes) {
        if (bytes.length < bufferSize()) {
            throw new IllegalArgumentException("insufficient bytes");
        }

        if (bytes[0] != WebSocketHeader.FINAL_OPCODE_BINARY) {
            _selectedTransportWrapper = _wrapper2;
            return;
        }

        _selectedTransportWrapper = _wrapper1;
    }
}
