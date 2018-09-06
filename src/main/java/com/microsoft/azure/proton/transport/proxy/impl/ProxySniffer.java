package com.microsoft.azure.proton.transport.proxy.impl;

import org.apache.qpid.proton.engine.impl.HandshakeSniffingTransportWrapper;
import org.apache.qpid.proton.engine.impl.TransportWrapper;

public class ProxySniffer extends HandshakeSniffingTransportWrapper<TransportWrapper, TransportWrapper> {
    public ProxySniffer(TransportWrapper webSocket, TransportWrapper other) {
        super(webSocket, other);
    }

    @Override
    protected int bufferSize() {
        return 0;
    }

    @Override
    protected void makeDetermination(byte[] bytes) {
        _selectedTransportWrapper = _wrapper2;
    }
}
