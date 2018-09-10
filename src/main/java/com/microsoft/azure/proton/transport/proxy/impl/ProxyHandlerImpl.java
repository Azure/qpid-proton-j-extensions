package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.ProxyHandler;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

public class ProxyHandlerImpl implements ProxyHandler {

    @Override
    public String createProxyRequest(String hostName, Map<String, String> additionalHeaders) {
        final String endOfLine = "\r\n";
        final StringBuilder connectRequestBuilder = new StringBuilder();
        connectRequestBuilder.append(
                String.format(
                        "CONNECT %1$s HTTP/1.1%2$sHost: %1$s%2$sConnection: Keep-Alive%2$s",
                        hostName,
                        endOfLine));
        if (additionalHeaders != null) {
            for (Map.Entry<String, String> entry: additionalHeaders.entrySet()) {
                connectRequestBuilder.append(entry.getKey());
                connectRequestBuilder.append(": ");
                connectRequestBuilder.append(entry.getValue());
                connectRequestBuilder.append(endOfLine);
            }
        }
        connectRequestBuilder.append(endOfLine);
        return connectRequestBuilder.toString();
    }

    @Override
    public ProxyResponseResult validateProxyResponse(ByteBuffer buffer) {
        int size = buffer.remaining();
        String response = null;

        if (size > 0) {
            byte[] responseBytes = new byte[buffer.remaining()];
            buffer.get(responseBytes);
            response = new String(responseBytes, StandardCharsets.UTF_8);
            final Scanner responseScanner = new Scanner(response);
            if (responseScanner.hasNextLine()) {
                final String firstLine = responseScanner.nextLine();
                if (firstLine.toLowerCase().contains("http/1.1")
                        && firstLine.contains("200")
                        && firstLine.toLowerCase().contains("connection established")) {
                    return new ProxyResponseResult(true, response);
                }
            }
        }

        return new ProxyResponseResult(false, response);
    }
}
