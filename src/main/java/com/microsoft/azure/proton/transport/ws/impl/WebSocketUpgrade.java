/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.ws.impl;

import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Scanner;

public class WebSocketUpgrade {
    private final String rfcGuid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final char questionMark = '?';
    private final char slash = '/';
    private final String query;
    private final String host;
    private final String path;
    private final String port;
    private final String protocol;
    private final Map<String, String> additionalHeaders;

    private volatile String webSocketKey = "";

    /**
     * Create {@link WebSocketUpgrade} instance, which can be used for websocket upgrade hand-shake with http server
     * as per RFC https://tools.ietf.org/html/rfc6455.
     * @param hostName host name to send the request to
     * @param webSocketPath path on the request url where WebSocketUpgrade will be sent to
     * @param webSocketQuery query on the request url where WebSocketUpgrade will be sent to
     * @param webSocketPort port on the request url where WebSocketUpgrade will be sent to
     * @param webSocketProtocol value for Sec-WebSocket-Protocol header on the WebSocketUpgrade request
     * @param additionalHeaders any additional headers to be part of the WebSocketUpgrade request
     */
    public WebSocketUpgrade(
            String hostName,
            String webSocketPath,
            String webSocketQuery,
            int webSocketPort,
            String webSocketProtocol,
            Map<String, String> additionalHeaders) {
        this.host = hostName;
        this.path = webSocketPath.isEmpty() || webSocketPath.charAt(0) == this.slash
                ? webSocketPath
                : this.slash + webSocketPath;
        this.query = webSocketQuery.isEmpty() || webSocketQuery.charAt(0) == this.questionMark
                ? webSocketQuery
                : this.questionMark + webSocketQuery;
        this.port = webSocketPort == 0 ? "" : String.valueOf(webSocketPort);
        this.protocol = webSocketProtocol;
        this.additionalHeaders = additionalHeaders;
    }

    /**
     * Utility function to create random, Base64 encoded key.
     */
    private String createWebSocketKey() {
        byte[] key = new byte[16];

        for (int i = 0; i < 16; i++) {
            key[i] = (byte) (int) (Math.random() * 256);
        }

        return Base64.encodeBase64StringLocal(key).trim();
    }

    /**
     * Create the Upgrade to websocket request as per the RFC https://tools.ietf.org/html/rfc6455.
     * @return http request to upgrade to websockets.
     */
    public String createUpgradeRequest() {
        if (this.host.isEmpty()) {
            throw new InvalidParameterException("host header has no value");
        }

        if (this.protocol.isEmpty()) {
            throw new InvalidParameterException("protocol header has no value");
        }

        this.webSocketKey = createWebSocketKey();

        final String endOfLine = "\r\n";
        final StringBuilder stringBuilder = new StringBuilder()
                .append("GET https://")
                .append(this.host)
                .append(this.path)
                .append(this.query)
                .append(" HTTP/1.1").append(endOfLine)
                .append("Connection: Upgrade,Keep-Alive").append(endOfLine)
                .append("Upgrade: websocket").append(endOfLine)
                .append("Sec-WebSocket-Version: 13").append(endOfLine)
                .append("Sec-WebSocket-Key: ").append(this.webSocketKey).append(endOfLine)
                .append("Sec-WebSocket-Protocol: ").append(this.protocol).append(endOfLine)
                .append("Host: ").append(this.host).append(endOfLine);

        if (additionalHeaders != null) {
            for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
                stringBuilder.append(entry.getKey() + ": " + entry.getValue()).append(endOfLine);
            }
        }

        stringBuilder.append(endOfLine);

        return stringBuilder.toString();
    }

    /**
     * Validate the response received for 'upgrade to websockets' request from http server.
     * @param responseBytes bytes received from http server
     * @return value indicating if the websockets upgrade succeeded
     */
    public Boolean validateUpgradeReply(byte[] responseBytes) {
        final String httpString = new String(responseBytes, StandardCharsets.UTF_8);

        Boolean isStatusLineOk = false;
        Boolean isUpgradeHeaderOk = false;
        Boolean isConnectionHeaderOk = false;
        Boolean isProtocolHeaderOk = false;
        Boolean isAcceptHeaderOk = false;

        final Scanner scanner = new Scanner(httpString);

        while (scanner.hasNextLine()) {
            final String line = scanner.nextLine();

            if ((line.toLowerCase().contains("http/1.1"))
                    && (line.contains("101"))
                    && (line.toLowerCase().contains("switching protocols"))) {
                isStatusLineOk = true;

                continue;
            }

            if ((line.toLowerCase().contains("upgrade")) && (line.toLowerCase().contains("websocket"))) {
                isUpgradeHeaderOk = true;

                continue;
            }

            if ((line.toLowerCase().contains("connection")) && (line.toLowerCase().contains("upgrade"))) {
                isConnectionHeaderOk = true;

                continue;
            }

            if (line.toLowerCase().contains("sec-websocket-protocol") && (line.toLowerCase().contains(this.protocol.toLowerCase()))) {
                isProtocolHeaderOk = true;

                continue;
            }

            if (line.toLowerCase().contains("sec-websocket-accept")) {
                MessageDigest messageDigest = null;

                try {
                    messageDigest = MessageDigest.getInstance("SHA-1");
                } catch (NoSuchAlgorithmException e) {
                    // can't happen since SHA-1 is a known digest
                    break;
                }

                final String expectedKey = Base64.encodeBase64StringLocal(
                        messageDigest.digest((this.webSocketKey + this.rfcGuid).getBytes())).trim();

                if (line.contains(expectedKey)) {
                    isAcceptHeaderOk = true;
                }

                continue;
            }
        }

        scanner.close();

        if ((isStatusLineOk) && (isUpgradeHeaderOk) && (isConnectionHeaderOk) && (isProtocolHeaderOk) && (isAcceptHeaderOk)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("WebSocketUpgrade [host=")
                .append(host)
                .append(", path=")
                .append(path)
                .append(", port=")
                .append(port)
                .append(", protocol=")
                .append(protocol)
                .append(", webSocketKey=")
                .append(webSocketKey);

        if ((additionalHeaders != null) && (!additionalHeaders.isEmpty())) {
            builder.append(", additionalHeaders=");

            for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
                builder.append(entry.getKey() + ":" + entry.getValue()).append(", ");
            }

            final int lastIndex = builder.lastIndexOf(", ");
            builder.delete(lastIndex, lastIndex + 2);
        }

        builder.append("]");

        return builder.toString();
    }
}
