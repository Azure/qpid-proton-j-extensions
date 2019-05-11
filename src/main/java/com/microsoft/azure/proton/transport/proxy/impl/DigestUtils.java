package com.microsoft.azure.proton.transport.proxy.impl;

class DigestUtils {
    private static final char[] HEX_CHARACTERS = "0123456789abcdef".toCharArray();

    /**
     * Converts a byte array to its hex string representation.
     * From: https://stackoverflow.com/a/9855338
     */
    static String convertToHexString(byte[] bytes) {
        final char[] hexChars = new char[bytes.length * 2];

        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_CHARACTERS[v >>> 4];
            hexChars[j * 2 + 1] = HEX_CHARACTERS[v & 0x0F];
        }

        return new String(hexChars);
    }
}
