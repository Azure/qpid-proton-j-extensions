package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.ProxyHandler;

import java.nio.ByteBuffer;
import java.util.Map;

public class ProxyHandlerImpl implements ProxyHandler {

    @Override
    public String createProxyRequest(String hostName, Map<String, String> additionalHeaders) {
        String connectRequest = "CONNECT %s HTTP/1.1\r\nHost: %s\r\nConnection: Keep-Alive\r\n";
        connectRequest = String.format(connectRequest, hostName, hostName);
        connectRequest = connectRequest.concat("\r\n");
        return connectRequest;
    }

    @Override
    public Boolean validateProxyReply(ByteBuffer buffer) {
        int size = buffer.remaining();

        if (size > 0) {
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
        }

        // TODO: validate the actual call
        return true;
    }
}
