// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.ws;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Handles states for the web socket.
 */
public interface WebSocketHandler {
    /**
     * States when parsing a frame.
     */
    enum WebSocketMessageType {
        /**
         * Unknown frame.
         */
        WEB_SOCKET_MESSAGE_TYPE_UNKNOWN,
        /**
         * Frame received is part of a multi-framed message.
         */
        WEB_SOCKET_MESSAGE_TYPE_CHUNK,
        /**
         * Header frame received is part of a multi-framed message.
         */
        WEB_SOCKET_MESSAGE_TYPE_HEADER_CHUNK,
        /**
         * Frame received is AMQP data.
         */
        WEB_SOCKET_MESSAGE_TYPE_AMQP,
        /**
         * Received a PING from the endpoint.
         */
        WEB_SOCKET_MESSAGE_TYPE_PING,
        /**
         * Receiving has completed because a close message was received.
         */
        WEB_SOCKET_MESSAGE_TYPE_CLOSE,
    }

    /**
     * Creates an HTTP request to upgrade to use web sockets.
     *
     * @param hostName Name of the host.
     * @param webSocketPath Path for the websocket.
     * @param webSocketQuery Query for the web socket.
     * @param webSocketPort Port for web socket.
     * @param webSocketProtocol Protocol to use for web sockets.
     * @param additionalHeaders Any additional headers to add to the HTTP upgrade request.
     * @return Represents the HTTP request.
     */
    String createUpgradeRequest(
            String hostName,
            String webSocketPath,
            String webSocketQuery,
            int webSocketPort,
            String webSocketProtocol,
            Map<String, String> additionalHeaders);

    /**
     * Validates the response.
     *
     * @param buffer ByteBuffer to read from.
     * @return True if the response is valid, otherwise, false.
     */
    Boolean validateUpgradeReply(ByteBuffer buffer);

    /**
     * Wraps the source buffer with additional contents from the web socket.
     *
     * @param srcBuffer Source buffer to wrap input.
     * @param dstBuffer Output buffer that bytes are written to.
     */
    void wrapBuffer(ByteBuffer srcBuffer, ByteBuffer dstBuffer);

    /**
     * Unwraps the layer from the buffer.
     *
     * @param srcBuffer The source buffer.
     * @return The current chunk for the web socket when reading.
     */
    WebsocketTuple unwrapBuffer(ByteBuffer srcBuffer);

    /**
     * Creates the pong for the "keep-alive", heart beat, network status probing when connecting in a web socket.
     *
     * @param srcBuffer The source buffer to read from.
     * @param dstBuffer The destination buffer with the pong.
     * @see <a href="https://html.spec.whatwg.org/multipage/web-sockets.html#ping-and-pong-frames">Ping and pong</a>
     */
    void createPong(ByteBuffer srcBuffer, ByteBuffer dstBuffer);

    /**
     * Gets the size of the header.
     *
     * @param payloadSize Size of the payload.
     * @return The size of the header.
     */
    int calculateHeaderSize(int payloadSize);

    /**
     * Represents the web socket message and its type.
     */
    class WebsocketTuple {

        private long length;
        private WebSocketMessageType type;

        /**
         * Creates an instance with the given length and type.
         *
         * @param length Length of the segment.
         * @param type Type of the socket message.
         */
        public WebsocketTuple(long length, WebSocketMessageType type) {
            this.length = length;
            this.type = type;
        }

        /**
         * Sets the length of the message.
         *
         * @param length The length of the message.
         */
        public void setLength(long length) {
            this.length = length;
        }

        /**
         * Sets the message type.
         *
         * @param type The message type.
         */
        public void setType(WebSocketMessageType type) {
            this.type = type;
        }

        /**
         * Gets the length of the message.
         *
         * @return The length of the message.
         */
        public long getLength() {
            return this.length;
        }

        /**
         * Gets the type of the message.
         *
         * @return The type of the message.
         */
        public WebSocketMessageType getType() {
            return this.type;
        }
    }
}
