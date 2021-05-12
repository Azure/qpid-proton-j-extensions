// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.ws;

/**
 * Represents a web socket header.
 *
 * +---------------------------------------------------------------+
 * 0                   1                   2                   3   |
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 |
 * +-+-+-+-+-------+-+-------------+-------------------------------+
 * |F|R|R|R| opcode|M| Payload len |   Extended payload length     |
 * |I|S|S|S|  (4)  |A|     (7)     |            (16/64)            |
 * |N|V|V|V|       |S|             |  (if payload len==126/127)    |
 * | |1|2|3|       |K|             |                               |
 * +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
 * |     Extended payload length continued, if payload len == 127  |
 * + - - - - - - - - - - - - - - - +-------------------------------+
 * |                               | Masking-key, if MASK set to 1 |
 * +-------------------------------+-------------------------------+
 * | Masking-key (continued)       |          Payload Data         |
 * +-------------------------------- - - - - - - - - - - - - - - - +
 * :                     Payload Data continued ...                :
 * + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
 * |                     Payload Data continued ...                |
 * +---------------------------------------------------------------+
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6455#section-5.2">RFC6455: Base Framing Protocol</a>
 */
public interface WebSocketHeader {
    /**
     * Size of header if payload size is {@literal < 125} and there is <b>no</b> masking key.
     */
    byte MIN_HEADER_LENGTH = 2;
    /**
     * Size of header if payload size is {@literal < 125} and there is a masking key.
     */
    byte MIN_HEADER_LENGTH_MASKED = 6;

    /**
     * Size of the header if the payload size is represented in 7 + 16 bits and there is <b>no</b> masking key.
     */
    byte MED_HEADER_LENGTH_NOMASK = 4;
    /**
     * Size of the header if the payload size is represented in 7 + 16 bits and there is a masking key.
     */
    byte MED_HEADER_LENGTH_MASKED = 8;

    /**
     * Size of the header if the payload size is represented in 7 + 64 bits and there is <b>no</b> masking key.
     */
    byte MAX_HEADER_LENGTH_NOMASK = 10;
    /**
     * Size of the header if the payload size is represented in 7 + 64 bits and there is a masking key.
     */
    byte MAX_HEADER_LENGTH_MASKED = 14;

    // Masks
    /**
     * FIN denotes that this is the final fragment in a message.
     */
    byte FINBIT_MASK = (byte) 0x80;
    /**
     * Denotes whether the "Payload data" is masked. If set to 1, a masking key is present in 'masking-key' and this
     * will be used to unmask the "Payload data".
     */
    byte OPCODE_MASK = (byte) 0x0F;

    /**
     * Denotes a continuation frame.
     */
    byte OPCODE_CONTINUATION = (byte) 0x00;
    /**
     * Denotes a binary frame.
     */
    byte OPCODE_BINARY = (byte) 0x02;
    /**
     * Denotes a connection close.
     */
    byte OPCODE_CLOSE = (byte) 0x08;
    /**
     * Denotes a ping.
     */
    byte OPCODE_PING = (byte) 0x09;
    /**
     * Denotes a pong.
     */
    byte OPCODE_PONG = (byte) 0x0A;
    /**
     * Mask to get value of {@link #OPCODE_MASK}.
     */
    byte MASKBIT_MASK = (byte) 0x80;
    /**
     * Mask to get value of payload length.
     */
    byte PAYLOAD_MASK = (byte) 0x7F;

    /**
     * Determines whether it is the final frame for the message.
     */
    byte FINAL_OPCODE_BINARY = FINBIT_MASK | OPCODE_BINARY;

    /**
     * Maximum size in bytes for the payload when using 7 bits to represent the size.
     */
    byte PAYLOAD_SHORT_MAX = 0x7D;
    /**
     * Maximum size in bytes for the payload when using 7 + 16 bits to represent the size.
     */
    int PAYLOAD_MEDIUM_MAX = 0xFFFF;
    /**
     * Maximum size in bytes for the payload when using 7 + 64 bits to represent the size.
     */
    int PAYLOAD_LARGE_MAX = 0x7FFFFFFF;
    byte PAYLOAD_EXTENDED_16 = 0x7E;
    byte PAYLOAD_EXTENDED_64 = 0x7F;
}
