/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.ws.impl;

import static com.microsoft.azure.proton.transport.ws.WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_HEADER_CHUNK;
import static com.microsoft.azure.proton.transport.ws.WebSocketHandler.WebSocketMessageType.WEB_SOCKET_MESSAGE_TYPE_UNKNOWN;
import static org.apache.qpid.proton.engine.impl.ByteBufferUtils.newWriteableBuffer;
import static org.apache.qpid.proton.engine.impl.ByteBufferUtils.pourAll;

import com.microsoft.azure.proton.transport.ws.WebSocket;
import com.microsoft.azure.proton.transport.ws.WebSocketHandler;
import com.microsoft.azure.proton.transport.ws.WebSocketHeader;

import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.TransportException;
import org.apache.qpid.proton.engine.impl.ByteBufferUtils;
import org.apache.qpid.proton.engine.impl.PlainTransportWrapper;
import org.apache.qpid.proton.engine.impl.TransportInput;
import org.apache.qpid.proton.engine.impl.TransportLayer;
import org.apache.qpid.proton.engine.impl.TransportOutput;
import org.apache.qpid.proton.engine.impl.TransportWrapper;

public class WebSocketImpl implements WebSocket, TransportLayer {
    private int maxFrameSize = (4 * 1024) + (16 * WebSocketHeader.MED_HEADER_LENGTH_MASKED);
    private boolean tailClosed = false;
    private final ByteBuffer inputBuffer;
    private boolean headClosed = false;
    private final ByteBuffer outputBuffer;
    private ByteBuffer pingBuffer;
    private ByteBuffer wsInputBuffer;
    private ByteBuffer tempBuffer;

    private int underlyingOutputSize = 0;
    private int webSocketHeaderSize = 0;

    private WebSocketHandler webSocketHandler;
    private WebSocketState webSocketState = WebSocketState.PN_WS_NOT_STARTED;

    private String host = "";
    private String path = "";
    private String query = "";
    private int port = 0;
    private String protocol = "";
    private Map<String, String> additionalHeaders = null;

    protected Boolean isWebSocketEnabled;

    private WebSocketHandler.WebSocketMessageType lastType;
    private long lastLength;
    private long bytesRead = 0;
    private WebSocketFrameReadState frameReadState = WebSocketFrameReadState.INIT_READ;

    /**
     * Create WebSocket transport layer - which, after configuring using
     * the {@link #configure(String, String, String, int, String, Map, WebSocketHandler)} API
     * is ready for layering in qpid-proton-j transport layers, using
     * {@link org.apache.qpid.proton.engine.impl.TransportInternal#addTransportLayer(TransportLayer)} API.
     */
    public WebSocketImpl() {
        inputBuffer = newWriteableBuffer(maxFrameSize);
        outputBuffer = newWriteableBuffer(maxFrameSize);
        pingBuffer = newWriteableBuffer(maxFrameSize);
        wsInputBuffer = newWriteableBuffer(maxFrameSize);
        tempBuffer = newWriteableBuffer(maxFrameSize);
        lastType = WEB_SOCKET_MESSAGE_TYPE_UNKNOWN;
        lastLength = 0;
        isWebSocketEnabled = false;
    }

    @Override
    public TransportWrapper wrap(final TransportInput input, final TransportOutput output) {
        return new WebSocketSniffer(new WebSocketTransportWrapper(input, output), new PlainTransportWrapper(output, input)) {
            protected boolean isDeterminationMade() {
                _selectedTransportWrapper = _wrapper1;
                return true;
            }
        };
    }

    @Override
    public void configure(
            String host,
            String path,
            String query,
            int port,
            String protocol,
            Map<String, String> additionalHeaders,
            WebSocketHandler webSocketHandler) {
        this.host = host;
        this.path = path;
        this.query = query;
        this.port = port;
        this.protocol = protocol;
        this.additionalHeaders = additionalHeaders;

        if (webSocketHandler != null) {
            this.webSocketHandler = webSocketHandler;
        } else {
            this.webSocketHandler = new WebSocketHandlerImpl();
        }

        isWebSocketEnabled = true;
    }

    @Override
    public void wrapBuffer(ByteBuffer srcBuffer, ByteBuffer dstBuffer) {
        if (isWebSocketEnabled) {
            webSocketHandler.wrapBuffer(srcBuffer, dstBuffer);
        } else {
            dstBuffer.clear();
            dstBuffer.put(srcBuffer);
        }
    }

    @Override
    public WebSocketHandler.WebsocketTuple unwrapBuffer(ByteBuffer buffer) {
        if (isWebSocketEnabled) {
            return webSocketHandler.unwrapBuffer(buffer);
        } else {
            return new WebSocketHandler.WebsocketTuple(0, WEB_SOCKET_MESSAGE_TYPE_UNKNOWN);
        }
    }

    @Override
    public WebSocketState getState() {
        return webSocketState;
    }

    @Override
    public ByteBuffer getOutputBuffer() {
        return outputBuffer;
    }

    @Override
    public ByteBuffer getInputBuffer() {
        return inputBuffer;
    }

    @Override
    public ByteBuffer getPingBuffer() {
        return pingBuffer;
    }

    @Override
    public ByteBuffer getWsInputBuffer() {
        return wsInputBuffer;
    }

    @Override
    public Boolean getEnabled() {
        return isWebSocketEnabled;
    }

    @Override
    public WebSocketHandler getWebSocketHandler() {
        return webSocketHandler;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(
                "WebSocketImpl [isWebSocketEnabled=").append(isWebSocketEnabled)
                .append(", state=").append(webSocketState)
                .append(", protocol=").append(protocol)
                .append(", host=").append(host)
                .append(", path=").append(path)
                .append(", query=").append(query)
                .append(", port=").append(port);

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

    protected void writeUpgradeRequest() {
        outputBuffer.clear();
        String request = webSocketHandler.createUpgradeRequest(host, path, query, port, protocol, additionalHeaders);
        outputBuffer.put(request.getBytes());
    }

    protected void writePong() {
        webSocketHandler.createPong(pingBuffer, outputBuffer);
    }

    protected void writeClose() {
        outputBuffer.clear();
        pingBuffer.flip();
        outputBuffer.put(pingBuffer);
    }

    private class WebSocketTransportWrapper implements TransportWrapper {
        private final TransportInput underlyingInput;
        private final TransportOutput underlyingOutput;
        private final ByteBuffer head;

        private WebSocketTransportWrapper(TransportInput input, TransportOutput output) {
            underlyingInput = input;
            underlyingOutput = output;
            head = outputBuffer.asReadOnlyBuffer();
            head.limit(0);
        }

        private void readInputBuffer() {
            ByteBufferUtils.pour(inputBuffer, tempBuffer);
        }

        private boolean sendToUnderlyingInput() {
            boolean readComplete = false;
            switch (lastType) {
                case WEB_SOCKET_MESSAGE_TYPE_UNKNOWN:
                    wsInputBuffer.position(wsInputBuffer.limit());
                    wsInputBuffer.limit(wsInputBuffer.capacity());
                    break;
                case WEB_SOCKET_MESSAGE_TYPE_CHUNK:
                    wsInputBuffer.position(wsInputBuffer.limit());
                    wsInputBuffer.limit(wsInputBuffer.capacity());
                    break;
                case WEB_SOCKET_MESSAGE_TYPE_AMQP:
                    wsInputBuffer.flip();

                    int bytes2 = pourAll(wsInputBuffer, underlyingInput);
                    if (bytes2 == Transport.END_OF_STREAM) {
                        tailClosed = true;
                    }
                    //underlyingInput.process();

                    wsInputBuffer.compact();
                    wsInputBuffer.flip();
                    readComplete = true;
                    break;
                case WEB_SOCKET_MESSAGE_TYPE_CLOSE:
                    wsInputBuffer.flip();
                    pingBuffer.put(wsInputBuffer);
                    webSocketState = WebSocketState.PN_WS_CONNECTED_CLOSING;

                    wsInputBuffer.compact();
                    wsInputBuffer.flip();
                    readComplete = true;
                    break;
                case WEB_SOCKET_MESSAGE_TYPE_PING:
                    wsInputBuffer.flip();
                    pingBuffer.put(wsInputBuffer);
                    webSocketState = WebSocketState.PN_WS_CONNECTED_PONG;

                    wsInputBuffer.compact();
                    wsInputBuffer.flip();
                    readComplete = true;
                    break;
                default:
                    assert false : String.format("unexpected value for WebSocketFrameReadState: %s", lastType);
            }
            wsInputBuffer.position(wsInputBuffer.limit());
            wsInputBuffer.limit(wsInputBuffer.capacity());
            return readComplete;
        }

        private void processInput() throws TransportException {
            switch (webSocketState) {
                case PN_WS_CONNECTING:
                    if (webSocketHandler.validateUpgradeReply(inputBuffer)) {
                        webSocketState = WebSocketState.PN_WS_CONNECTED_FLOW;
                    }
                    inputBuffer.compact();
                    break;
                case PN_WS_CONNECTED_FLOW:
                case PN_WS_CONNECTED_PONG:

                    if (inputBuffer.remaining() > 0) {
                        boolean readComplete = false;
                        while (!readComplete) {
                            switch (frameReadState) {
                                //State 1: Init_Read
                                case INIT_READ:
                                    //Reset the bytes read count
                                    bytesRead = 0;
                                    //Determine how much to grab from the input buffer and only take that
                                    readInputBuffer();

                                    frameReadState = tempBuffer.position() < 2
                                            ? WebSocketFrameReadState.CHUNK_READ
                                            : WebSocketFrameReadState.HEADER_READ;
                                    readComplete = frameReadState == WebSocketFrameReadState.CHUNK_READ;
                                    break;

                                //State 2: Chunk_Read
                                case CHUNK_READ:
                                    //Determine how much to grab from the input buffer and only take that
                                    readInputBuffer();

                                    frameReadState = tempBuffer.position() < 2 ? frameReadState : WebSocketFrameReadState.HEADER_READ;
                                    readComplete = frameReadState == WebSocketFrameReadState.CHUNK_READ;
                                    break;

                                //State 3: Header_Read
                                case HEADER_READ:
                                    //Determine how much to grab from the input buffer and only take that
                                    readInputBuffer();

                                    tempBuffer.flip();
                                    WebSocketHandler.WebsocketTuple unwrapResult = unwrapBuffer(tempBuffer);
                                    lastType = unwrapResult.getType();
                                    lastLength = unwrapResult.getLength();

                                    frameReadState = lastType == WEB_SOCKET_MESSAGE_TYPE_HEADER_CHUNK
                                            ? WebSocketFrameReadState.CHUNK_READ
                                            : WebSocketFrameReadState.CONTINUED_FRAME_READ;
                                    readComplete = frameReadState == WebSocketFrameReadState.CHUNK_READ
                                            || tempBuffer.position() == tempBuffer.limit();

                                    if (frameReadState == WebSocketFrameReadState.CONTINUED_FRAME_READ) {
                                        tempBuffer.compact();
                                    } else {
                                        //Unflip the buffer to continue writing to it
                                        tempBuffer.position(tempBuffer.limit());
                                        tempBuffer.limit(tempBuffer.capacity());
                                    }

                                    break;

                                //State 4: Continued_Frame_Read (Similar to Chunk_Read but reading until
                                // we've read the number of bytes specified when unwrapping the buffer)
                                case CONTINUED_FRAME_READ:
                                    //Determine how much to grab from the input buffer and only take that
                                    readInputBuffer();
                                    tempBuffer.flip();

                                    final byte[] data;
                                    if (tempBuffer.remaining() >= lastLength - bytesRead) {
                                        data = new byte[(int) (lastLength - bytesRead)];
                                        tempBuffer.get(data, 0, (int) (lastLength - bytesRead));
                                        wsInputBuffer.put(data);
                                        bytesRead += lastLength - bytesRead;
                                    } else {
                                        //Otherwise the remaining bytes is < the rest that we need
                                        data = new byte[tempBuffer.remaining()];
                                        tempBuffer.get(data);
                                        wsInputBuffer.put(data);
                                        bytesRead += data.length;
                                    }

                                    //Send whatever we have
                                    sendToUnderlyingInput();

                                    frameReadState = bytesRead
                                            == lastLength ? WebSocketFrameReadState.INIT_READ : WebSocketFrameReadState.CONTINUED_FRAME_READ;
                                    readComplete = tempBuffer.remaining() == 0;
                                    tempBuffer.compact();
                                    break;

                                //State 5: Read_Error
                                case READ_ERROR:
                                    break;

                                default:
                                    assert false : String.format("unexpected value for WebSocketFrameReadState: %s", frameReadState);
                            }
                        }
                    }
                    inputBuffer.compact();
                    break;
                case PN_WS_NOT_STARTED:
                case PN_WS_CLOSED:
                case PN_WS_FAILED:
                default:
                    break;
            }
        }

        @Override
        public int capacity() {
            if (isWebSocketEnabled) {
                if (tailClosed) {
                    return Transport.END_OF_STREAM;
                } else {
                    return inputBuffer.remaining();
                }
            } else {
                return underlyingInput.capacity();
            }
        }

        @Override
        public int position() {
            if (isWebSocketEnabled) {
                if (tailClosed) {
                    return Transport.END_OF_STREAM;
                } else {
                    return inputBuffer.position();
                }
            } else {
                return underlyingInput.position();
            }
        }

        @Override
        public ByteBuffer tail() {
            if (isWebSocketEnabled) {
                return inputBuffer;
            } else {
                return underlyingInput.tail();
            }
        }

        @Override
        public void process() throws TransportException {
            if (isWebSocketEnabled) {
                inputBuffer.flip();

                switch (webSocketState) {
                    case PN_WS_CONNECTING:
                    case PN_WS_CONNECTED_FLOW:
                        processInput();
                        break;
                    case PN_WS_NOT_STARTED:
                    case PN_WS_FAILED:
                    default:
                        underlyingInput.process();
                }
            } else {
                underlyingInput.process();
            }
        }

        @Override
        public void close_tail() {
            tailClosed = true;
            if (isWebSocketEnabled) {
                headClosed = true;
                underlyingInput.close_tail();
            } else {
                underlyingInput.close_tail();
            }
        }

        @Override
        public int pending() {
            if (isWebSocketEnabled) {
                switch (webSocketState) {
                    case PN_WS_NOT_STARTED:
                        if (outputBuffer.position() == 0) {
                            webSocketState = WebSocketState.PN_WS_CONNECTING;

                            writeUpgradeRequest();

                            head.limit(outputBuffer.position());

                            if (headClosed) {
                                webSocketState = WebSocketState.PN_WS_FAILED;
                                return Transport.END_OF_STREAM;
                            } else {
                                return outputBuffer.position();
                            }
                        } else {
                            return outputBuffer.position();
                        }
                    case PN_WS_CONNECTING:

                        if (headClosed && (outputBuffer.position() == 0)) {
                            webSocketState = WebSocketState.PN_WS_FAILED;
                            return Transport.END_OF_STREAM;
                        } else {
                            return outputBuffer.position();
                        }
                    case PN_WS_CONNECTED_FLOW:
                        underlyingOutputSize = underlyingOutput.pending();

                        if (underlyingOutputSize > 0) {
                            webSocketHeaderSize = webSocketHandler.calculateHeaderSize(underlyingOutputSize);
                            return underlyingOutputSize + webSocketHeaderSize;
                        } else {
                            return underlyingOutputSize;
                        }
                    case PN_WS_CONNECTED_PONG:
                        webSocketState = WebSocketState.PN_WS_CONNECTED_FLOW;

                        writePong();

                        head.limit(outputBuffer.position());

                        if (headClosed) {
                            webSocketState = WebSocketState.PN_WS_FAILED;
                            return Transport.END_OF_STREAM;
                        } else {
                            return outputBuffer.position();
                        }
                    case PN_WS_CONNECTED_CLOSING:
                        webSocketState = WebSocketState.PN_WS_CLOSED;

                        writeClose();

                        head.limit(outputBuffer.position());

                        if (headClosed) {
                            webSocketState = WebSocketState.PN_WS_FAILED;
                            return Transport.END_OF_STREAM;
                        } else {
                            return outputBuffer.position();
                        }
                    case PN_WS_FAILED:
                    default:
                        return Transport.END_OF_STREAM;
                }
            } else {
                return underlyingOutput.pending();
            }
        }

        @Override
        public ByteBuffer head() {
            if (isWebSocketEnabled) {
                switch (webSocketState) {
                    case PN_WS_CONNECTING:
                    case PN_WS_CONNECTED_PONG:
                    case PN_WS_CONNECTED_CLOSING:
                        return head;
                    case PN_WS_CONNECTED_FLOW:
                        underlyingOutputSize = underlyingOutput.pending();

                        if (underlyingOutputSize > 0) {
                            wrapBuffer(underlyingOutput.head(), outputBuffer);

                            webSocketHeaderSize = outputBuffer.position() - underlyingOutputSize;

                            head.limit(outputBuffer.position());
                        }

                        return head;
                    case PN_WS_NOT_STARTED:
                    case PN_WS_CLOSED:
                    case PN_WS_FAILED:
                    default:
                        return underlyingOutput.head();
                }

            } else {
                return underlyingOutput.head();
            }
        }

        @Override
        public void pop(int bytes) {
            if (isWebSocketEnabled) {
                switch (webSocketState) {
                    case PN_WS_CONNECTING:
                        if (outputBuffer.position() != 0) {
                            outputBuffer.flip();
                            outputBuffer.position(bytes);
                            outputBuffer.compact();
                            head.position(0);
                            head.limit(outputBuffer.position());
                        } else {
                            underlyingOutput.pop(bytes);
                        }
                        break;
                    case PN_WS_CONNECTED_FLOW:
                    case PN_WS_CONNECTED_PONG:
                    case PN_WS_CONNECTED_CLOSING:
                        if ((bytes >= webSocketHeaderSize) && (outputBuffer.position() != 0)) {
                            outputBuffer.flip();
                            outputBuffer.position(bytes);
                            outputBuffer.compact();
                            head.position(0);
                            head.limit(outputBuffer.position());
                            underlyingOutput.pop(bytes - webSocketHeaderSize);
                            webSocketHeaderSize = 0;
                        } else if ((bytes > 0) && (bytes < webSocketHeaderSize)) {
                            webSocketHeaderSize -= bytes;
                        } else {
                            underlyingOutput.pop(bytes);
                        }
                        break;
                    case PN_WS_NOT_STARTED:
                    case PN_WS_CLOSED:
                    case PN_WS_FAILED:
                        underlyingOutput.pop(bytes);
                        break;
                    default:
                        assert false : String.format("unexpected value for WebSocketFrameReadState: %s", webSocketState);
                }
            } else {
                underlyingOutput.pop(bytes);
            }
        }

        @Override
        public void close_head() {
            underlyingOutput.close_head();
        }

        public final char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

        private String convertToHex(byte[] bb) {
            final int lgt = bb.length;

            final char[] out = new char[5 * lgt];
            for (int i = 0, j = 0; i < lgt; i++) {
                out[j++] = '0';
                out[j++] = 'x';
                out[j++] = hexDigits[(0xF0 & bb[i]) >>> 4];
                out[j++] = hexDigits[0x0F & bb[i]];
                out[j++] = '|';
            }
            return new String(out);
        }

        private String convertToHex(ByteBuffer bb) {
            final byte[] data = new byte[bb.remaining()];
            bb.duplicate().get(data);

            return convertToHex(data);
        }

        private String convertToBinary(byte[] bb) {
            StringBuilder sb = new StringBuilder();

            for (byte b : bb) {
                sb.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
                sb.append('|');
            }

            return sb.toString();
        }

        private String convertToBinary(ByteBuffer bb) {
            final byte[] data = new byte[bb.remaining()];
            bb.duplicate().get(data);

            return convertToBinary(data);
        }
    }
}