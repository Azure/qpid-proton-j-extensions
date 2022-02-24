// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.HttpStatusLine;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class HTTPStatusLineTest {

    /**
     * Verifies that it successfully parses a valid HTTP status line.
     */
    @Test
    public void validStatusLine() {
        // Arrange
        final String line = "HTTP/1.1 200 Connection Established";

        // Act
        final HttpStatusLine actual = HttpStatusLine.create(line);

        // Assert
        Assert.assertNotNull(actual);

        Assert.assertEquals(200, actual.getStatusCode());
        Assert.assertEquals("1.1", actual.getProtocolVersion());
        Assert.assertEquals("Connection Established", actual.getReason());
    }


    /**
     * Verifies that status line length is invalid
     */
    @ParameterizedTest
    @ValueSource(strings = {"HTTP/1.1 InvalidLength", "HTTP/1.1 Invalid Code", "HTTP/invalid protocol"})
    public void invalidStatusLine(String line) {

        // Act & Assert
        Assert.assertThrows(IllegalArgumentException.class, () -> HttpStatusLine.create(line));
    }


}
