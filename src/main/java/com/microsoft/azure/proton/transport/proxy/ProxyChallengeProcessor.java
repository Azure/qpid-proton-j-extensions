package com.microsoft.azure.proton.transport.proxy;

import java.util.Map;

public interface ProxyChallengeProcessor {
    Map<String, String> getHeader(String challengeResp, String host);
}

