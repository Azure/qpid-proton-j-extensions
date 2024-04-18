// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.proxy.impl;

import java.util.List;
import java.util.Map;

import com.microsoft.azure.proton.transport.proxy.ChallengeResponse;

/**
 * The accessor helper for {@link ChallengeResponse}.
 */
public final class ChallengeResponseAccessHelper {
    private static ChallengeResponseAccessor accessor;

    /**
     * The accessor interface for {@link ChallengeResponse} construction.
     */
    public interface ChallengeResponseAccessor {
        /**
         * Create an instance of {@link ChallengeResponse} with the provided headers.
         *
         * @param headers the headers.
         * @return the created instance of {@link ChallengeResponse}.
         */
        ChallengeResponse internalCreate(Map<String, List<String>> headers);
    }

    /**
     * Sets the accessor.
     *
     * @param accessor the accessor.
     */
    public static void setAccessor(ChallengeResponseAccessor accessor) {
        ChallengeResponseAccessHelper.accessor = accessor;
    }

    /**
     * Creates an instance of {@link ChallengeResponse} with the provided headers.
     *
     * @param headers the headers.
     * @return the created instance of {@link ChallengeResponse}.
     */
    public static ChallengeResponse internalCreate(Map<String, List<String>> headers) {
        if (accessor == null) {
            try {
                Class.forName(ChallengeResponse.class.getName(), true,
                    ChallengeResponseAccessHelper.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        assert accessor != null;
        return accessor.internalCreate(headers);
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private ChallengeResponseAccessHelper() {
    }
}
