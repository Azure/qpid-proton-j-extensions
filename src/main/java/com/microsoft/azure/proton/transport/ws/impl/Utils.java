// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.ws.impl;

import java.security.SecureRandom;

/**
 * Utility methods.
 */
final class Utils {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Gets an instance of secure random.
     *
     * @return An instance of secure random.
     */
    static SecureRandom getSecureRandom() {
        return SECURE_RANDOM;
    }

    /**
     * So an instance of class cannot be created.
     */
    private Utils() {
    }
}
