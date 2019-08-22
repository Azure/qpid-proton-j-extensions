/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy;

import java.util.Locale;
import java.util.Objects;

/**
 * The first line in an HTTP 1.0/1.1 response. Consists of the HTTP protocol version, status code, and a reason phrase
 * for the HTTP response.
 *
 * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html">RFC 2616</a>
 */
public class HttpStatusLine {
    private final String httpVersion;
    private final int statusCode;
    private final String reason;

    /**
     * Creates a new instance of {@link HttpStatusLine}.
     *
     * @param protocolVersion The HTTP protocol version. For example, 1.0, 1.1.
     * @param statusCode A numeric status code for the HTTP response.
     * @param reason Textual phrase representing the HTTP status code.
     */
    private HttpStatusLine(String protocolVersion, int statusCode, String reason) {
        this.httpVersion = Objects.requireNonNull(protocolVersion, "'httpVersion' cannot be null.");
        this.statusCode = statusCode;
        this.reason = Objects.requireNonNull(reason, "'reason' cannot be null.");
    }

    /**
     * Parses the provided {@code statusLine} into an HTTP status line.
     *
     * @param line Line to parse into an HTTP status line.
     * @return A new instance of {@link HttpStatusLine} representing the given {@code statusLine}.
     * @throws IllegalArgumentException if {@code line} is not the correct format of an HTTP status line. If it
     *         does not have a protocol version, status code, or reason component. Or, if the HTTP protocol version
     *         cannot be parsed.
     */
    public static HttpStatusLine create(String line) {
        final String[] components = line.split(" ", 3);
        if (components.length != 3) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "HTTP status-line is invalid. Line: %s", line));
        }

        final String[] protocol = components[0].split("/", 2);
        if (protocol.length != 2) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "Protocol is invalid, expected HTTP/{version}. Actual: %s", components[0]));
        }

        return new HttpStatusLine(protocol[1], Integer.parseInt(components[1]), components[2]);
    }

    /**
     * Gets the HTTP protocol version.
     *
     * @return The HTTP protocol version.
     */
    public String getProtocolVersion() {
        return this.httpVersion;
    }

    /**
     * Gets the HTTP status code.
     *
     * @return The HTTP status code.
     */
    public int getStatusCode() {
        return this.statusCode;
    }

    /**
     * Gets the textual representation for the HTTP status code.
     *
     * @return The textual representation for the HTTP status code.
     */
    public String getReason() {
        return this.reason;
    }
}
