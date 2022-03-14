// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.proxy.impl;

/**
 * Utility classes for strings.
 */
class StringUtils {
    static final String NEW_LINE = "\r\n";

    static boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }
}
