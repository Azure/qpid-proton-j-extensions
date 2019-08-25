/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * Represents an HTTP response from a proxy.
 */
public interface ProxyResponse {
    /**
     * Gets the headers for the HTTP response.
     *
     * @return The headers for the HTTP response.
     */
    Map<String, List<String>> getHeaders();

    /**
     * Gets the HTTP status line.
     *
     * @return The HTTP status line.
     */
    HttpStatusLine getStatus();

    /**
     * Gets the HTTP response body.
     *
     * @return The HTTP response body.
     */
    ByteBuffer getContents();

    /**
     * Gets the HTTP response body as an error.
     *
     * @return If there is no HTTP response body, an empty string is returned.
     */
    String getError();

    /**
     * Gets whether or not the HTTP response is complete. An HTTP response is complete when the HTTP header and body are
     * received.
     *
     * @return {@code true} if the HTTP response is complete, and {@code false} otherwise.
     */
    boolean isMissingContent();

    /**
     * Adds contents to the body if it is missing content.
     *
     * @param contents Contents to add to the HTTP body.
     */
    void addContent(ByteBuffer contents);
}
