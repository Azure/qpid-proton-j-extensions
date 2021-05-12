package com.microsoft.azure.proton.transport.proxy;

import java.util.Map;

/**
 * Creates a set of headers to add to the HTTP request when responding to a proxy challenge.
 *
 * @see <a href="https://developer.mozilla.org/docs/Web/HTTP/Headers/Proxy-Authenticate">Proxy-Authenticate</a>
 */
public interface ProxyChallengeProcessor {
    /**
     * Gets headers to add to the HTTP request when a proxy challenge is issued.
     *
     * @return Headers to add to the HTTP request when a proxy challenge is issued.
     */
    Map<String, String> getHeader();
}

