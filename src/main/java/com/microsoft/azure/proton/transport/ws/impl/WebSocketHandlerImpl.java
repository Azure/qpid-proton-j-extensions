// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.ws.impl;

import com.microsoft.azure.proton.transport.ws.WebSocketHandler;
import com.microsoft.azure.proton.transport.ws.WebSocketHeader;

import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Implementation for {@link WebSocketHandler}.
 */
public class WebSocketHandlerImpl implements WebSocketHandler {
    private WebSocketUpgrade webSocketUpgrade = null;

    /**
     * Creates a new instance of WebSocketHandlerImpl.
     */
    public WebSocketHandlerImpl() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String createUpgradeRequest(
            String hostName,
            String webSocketPath,
            String webSocketQuery,
            int webSocketPort,
            String webSocketProtocol,
            Map<String, String> additionalHeaders) {
        webSocketUpgrade = createWebSocketUpgrade(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders);
        return webSocketUpgrade.createUpgradeRequest();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createPong(ByteBuffer ping, ByteBuffer pong) {
        if ((ping == null) || (pong == null)) {
            throw new IllegalArgumentException("input parameter cannot be null");
        }

        if (ping.capacity() > pong.capacity()) {
            throw new IllegalArgumentException("insufficient output buffer size");
        }

        if (ping.remaining() > 0) {
            byte[] buffer = ping.array();
            buffer[0] = WebSocketHeader.FINBIT_MASK | WebSocketHeader.OPCODE_PONG;

            pong.clear();
            pong.put(buffer);
        } else {
            pong.clear();
            pong.limit(0);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean validateUpgradeReply(ByteBuffer buffer) {
        Boolean retVal = false;

        if (webSocketUpgrade != null) {
            int size = buffer.remaining();

            if (size > 0) {
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);

                retVal = webSocketUpgrade.validateUpgradeReply(data);
                if (retVal) {
                    webSocketUpgrade = null;
                }
            }
        }

        return retVal;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void wrapBuffer(ByteBuffer srcBuffer, ByteBuffer dstBuffer) {
        if ((srcBuffer == null) || (dstBuffer == null)) {
            throw new IllegalArgumentException("input parameter is null");
        }

        if (srcBuffer.remaining() > 0) {
            // We always send masked data
            // RFC: "client MUST mask all frames that it sends to the server"
            final byte[] maskingKey = createRandomMaskingKey();

            // Get data length
            final int dataLength = srcBuffer.remaining();

            // Auto growing buffer for the WS frame, initialized to minimum size
            ByteArrayOutputStream webSocketFrame = new ByteArrayOutputStream(WebSocketHeader.MIN_HEADER_LENGTH_MASKED + dataLength);

            // Create the first byte
            // We always send final WebSocket frame
            // We always send binary message (AMQP)
            byte firstByte = (byte) (WebSocketHeader.FINBIT_MASK | WebSocketHeader.OPCODE_BINARY);
            webSocketFrame.write(firstByte);

            // Create the second byte
            // RFC: "client MUST mask all frames that it sends to the server"
            byte secondByte = WebSocketHeader.MASKBIT_MASK;

            // RFC: The length of the "Payload data", in bytes: if 0-125, that is the payload length.
            if (dataLength <= WebSocketHeader.PAYLOAD_SHORT_MAX) {
                secondByte = (byte) (secondByte | dataLength);
                webSocketFrame.write(secondByte);
            } else if (dataLength <= WebSocketHeader.PAYLOAD_MEDIUM_MAX) {
                // RFC: If 126, the following 2 bytes interpreted as a 16-bit unsigned integer are the payload length
                // Create payload byte
                secondByte = (byte) (secondByte | WebSocketHeader.PAYLOAD_EXTENDED_16);
                webSocketFrame.write(secondByte);

                // Create extended length bytes
                webSocketFrame.write((byte) (dataLength >>> 8));
                webSocketFrame.write((byte) (dataLength));
            } else {
                // RFC: If 127, the following 8 bytes interpreted as a 64-bit unsigned integer
                // (the most significant bit MUST be 0) are the payload length.
                // No need for "else if" because if it is longer than what 8 byte length can hold... all bets are off anyway
                secondByte = (byte) (secondByte | WebSocketHeader.PAYLOAD_EXTENDED_64);
                webSocketFrame.write(secondByte);

                webSocketFrame.write((byte) (dataLength >>> 56));
                webSocketFrame.write((byte) (dataLength >>> 48));
                webSocketFrame.write((byte) (dataLength >>> 40));
                webSocketFrame.write((byte) (dataLength >>> 32));
                webSocketFrame.write((byte) (dataLength >>> 24));
                webSocketFrame.write((byte) (dataLength >>> 16));
                webSocketFrame.write((byte) (dataLength >>> 8));
                webSocketFrame.write((byte) (dataLength));
            }

            // Write mask
            webSocketFrame.write(maskingKey[0]);
            webSocketFrame.write(maskingKey[1]);
            webSocketFrame.write(maskingKey[2]);
            webSocketFrame.write(maskingKey[3]);

            // Write masked data
            for (int i = 0; i < dataLength; i++) {
                byte nextByte = srcBuffer.get();
                nextByte ^= maskingKey[i % 4];
                webSocketFrame.write(nextByte);
            }

            // Copy frame to destination buffer
            dstBuffer.clear();
            if (dstBuffer.capacity() >= webSocketFrame.size()) {
                dstBuffer.put(webSocketFrame.toByteArray());
            } else {
                throw new OutOfMemoryError("insufficient output buffer size");
            }
        } else {
            dstBuffer.clear();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebsocketTuple unwrapBuffer(ByteBuffer srcBuffer) {
        WebsocketTuple result = new WebsocketTuple(0, WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_UNKNOWN);

        if (srcBuffer == null) {
            throw new IllegalArgumentException("input parameter is null");
        }

        WebSocketMessageType retVal = WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_UNKNOWN;

        if (srcBuffer.remaining() > WebSocketHeader.MIN_HEADER_LENGTH) {
            // Read the first byte
            byte firstByte = srcBuffer.get();

            // Get and check the opcode
            byte opcode = (byte) (firstByte & WebSocketHeader.OPCODE_MASK);

            // Read the second byte
            byte secondByte = srcBuffer.get();
            // The MASK bit is never used.
            // byte maskBit = (byte) (secondByte & WebSocketHeader.MASKBIT_MASK);
            byte payloadLength = (byte) (secondByte & WebSocketHeader.PAYLOAD_MASK);

            long finalPayloadLength = -1;

            // We want to be explicit about the WebSocket payload length because the RFC specifies these ranges.
            if (payloadLength <= WebSocketHeader.PAYLOAD_SHORT_MAX) {
                finalPayloadLength = payloadLength;
            } else if (payloadLength == WebSocketHeader.PAYLOAD_EXTENDED_16) {
                // Check if we have enough bytes to read
                try {
                    //Apply mask to turn into unsigned value
                    finalPayloadLength = srcBuffer.getShort() & 0xFFFF;
                } catch (BufferUnderflowException e) {
                    retVal = WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_HEADER_CHUNK;
                }
            } else if (payloadLength == WebSocketHeader.PAYLOAD_EXTENDED_64) {
                //Check if we have enough bytes to read
                try {
                    finalPayloadLength = srcBuffer.getLong();
                } catch (BufferUnderflowException e) {
                    retVal = WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_HEADER_CHUNK;
                }
            }

            if (retVal == WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_UNKNOWN) {
                if (opcode == WebSocketHeader.OPCODE_BINARY) {
                    retVal = WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_AMQP;
                } else if (opcode == WebSocketHeader.OPCODE_PING) {
                    retVal = WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_PING;
                } else if (opcode == WebSocketHeader.OPCODE_CLOSE) {
                    retVal = WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_CLOSE;
                } else if (opcode == WebSocketHeader.OPCODE_CONTINUATION) {
                    retVal = WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_CHUNK;
                } else {
                    retVal = WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_UNKNOWN;
                }
            }

            result.setLength(finalPayloadLength);
            result.setType(retVal);
        }

        return result;
    }

    /**
     * Creates the initial Connection: upgrade request to use WebSocket.
     *
     * @param hostName host name to send the request to
     * @param webSocketPath path on the request url where WebSocketUpgrade will be sent to
     * @param webSocketQuery query on the request url where WebSocketUpgrade will be sent to
     * @param webSocketPort port on the request url where WebSocketUpgrade will be sent to
     * @param webSocketProtocol value for Sec-WebSocket-Protocol header on the WebSocketUpgrade request
     * @param additionalHeaders any additional headers to be part of the WebSocketUpgrade request
     *
     * @return The upgrade request.
     */
    protected WebSocketUpgrade createWebSocketUpgrade(
            String hostName,
            String webSocketPath,
            String webSocketQuery,
            int webSocketPort,
            String webSocketProtocol,
            Map<String, String> additionalHeaders) {
        return new WebSocketUpgrade(hostName, webSocketPath, webSocketQuery, webSocketPort, webSocketProtocol, additionalHeaders);
    }


    /**
     * Returns a set of random bytes.
     *
     * @return a random set of 4 bytes.
     */
    protected byte[] createRandomMaskingKey() {
        final byte[] maskingKey = new byte[4];
        Utils.getSecureRandom().nextBytes(maskingKey);

        return maskingKey;
    }

    @Override
    public int calculateHeaderSize(int payloadSize) {
        int retVal = 0;
        if (payloadSize > 0) {
            if (payloadSize <= WebSocketHeader.PAYLOAD_SHORT_MAX) {
                retVal = WebSocketHeader.MIN_HEADER_LENGTH_MASKED;
            } else if (payloadSize <= WebSocketHeader.PAYLOAD_MEDIUM_MAX) {
                retVal = WebSocketHeader.MED_HEADER_LENGTH_MASKED;
            } else {
                retVal = WebSocketHeader.MAX_HEADER_LENGTH_MASKED;
            }
        }
        return retVal;
    }
}
