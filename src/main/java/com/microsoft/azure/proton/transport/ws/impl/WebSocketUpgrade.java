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
    public static final String RFC_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final char slash = '/';
    private String host = "";
    private String path = "";
    private String port = "";
    private String protocol = "";
    private String webSocketKey = "";
    private Map<String, String> additionalHeaders = null;
    private boolean certAvailability = false;

    /**
     * Create {@link WebSocketUpgrade} instance, which can be used for websocket upgrade hand-shake with http server
     * as per RFC https://tools.ietf.org/html/rfc6455.
     * @param hostName host name to send the request to
     * @param webSocketPath path on the request url where WebSocketUpgrade will be sent to
     * @param webSocketPort port on the request url where WebSocketUpgrade will be sent to
     * @param webSocketProtocol value for Sec-WebSocket-Protocol header on the WebSocketUpgrade request
     * @param additionalHeaders any additional headers to be part of the WebSocketUpgrade request
     */
    public WebSocketUpgrade(
            String hostName, String webSocketPath, int webSocketPort, String webSocketProtocol, Map<String, String> additionalHeaders) {
        setHost(hostName);
        setPath(webSocketPath);
        setPort(webSocketPort);
        setProtocol(webSocketProtocol);
        setAdditionalHeaders(additionalHeaders);
    }

    /**
     * Set host value in host header.
     *
     * @param host The host header field value.
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Set port value in host header.
     *
     * @param port The port header field value.
     */
    public void setPort(int port) {
        this.port = "";

        if (port != 0) {
            this.port = String.valueOf(port);
        }
    }

    /**
     * Set path value in handshake.
     *
     * @param path The path field value.
     */
    public void setPath(String path) {
        this.path = path;

        if (!this.path.isEmpty()) {
            if (this.path.charAt(0) != this.slash) {
                this.path = this.slash + this.path;
            }
        }
    }

    /**
     * Set protocol value in protocol header.
     *
     * @param protocol The protocol header field value.
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Add field-value pairs to HTTP header.
     *
     * @param additionalHeaders The Map containing the additional headers.
     */
    public void setAdditionalHeaders(Map<String, String> additionalHeaders) {
        this.additionalHeaders = additionalHeaders;
    }

    /**
     * Utility function to clear all additional headers.
     */
    public void clearAdditionalHeaders() {
        additionalHeaders.clear();
    }

    /**
     * Set protocol value in protocol header.
     */
    public void setClientCertAvailable() {
        certAvailability = true;
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

        String endOfLine = "\r\n";
        StringBuilder stringBuilder = new StringBuilder().append("GET https://").append(this.host).append(this.path)
                .append("?").append("iothub-no-client-cert=").append(!this.certAvailability)
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
        String httpString = new String(responseBytes, StandardCharsets.UTF_8);

        Boolean isStatusLineOk = false;
        Boolean isUpgradeHeaderOk = false;
        Boolean isConnectionHeaderOk = false;
        Boolean isProtocolHeaderOk = false;
        Boolean isAcceptHeaderOk = false;

        Scanner scanner = new Scanner(httpString);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

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

                String expectedKey = Base64.encodeBase64StringLocal(messageDigest.digest((this.webSocketKey + RFC_GUID).getBytes())).trim();

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
        StringBuilder builder = new StringBuilder();
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

            int lastIndex = builder.lastIndexOf(", ");
            builder.delete(lastIndex, lastIndex + 2);
        }

        builder.append("]");

        return builder.toString();
    }
}
