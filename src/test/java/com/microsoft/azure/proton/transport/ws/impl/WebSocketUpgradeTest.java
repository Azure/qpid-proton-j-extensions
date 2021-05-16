// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.proton.transport.ws.impl;

import org.junit.Test;

import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebSocketUpgradeTest {
    final String RFC_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    @Test
    public void testCreateUpgradeRequest_all_param() {

        String hostName = "host_XXX";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";
        String queryKey = "?iothub-no-client-cert=";
        String queryValue = "true";
        Map<String, String> additionalHeaders = new HashMap<String, String>();
        additionalHeaders.put("header1", "content1");
        additionalHeaders.put("header2", "content2");
        additionalHeaders.put("header3", "content3");

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath, queryKey + queryValue, webSocketPort, webSocketProtocol, additionalHeaders);

        String actual = webSocketUpgrade.createUpgradeRequest();

        Boolean isLineCountOk = false;
        Boolean isStatusLineOk = false;
        Boolean isUpgradeHeaderOk = false;
        Boolean isConnectionHeaderOk = false;
        Boolean isWebSocketVersionHeaderOk = false;
        Boolean isWebSocketKeyHeaderOk = false;
        Boolean isWebSocketProtocolHeaderOk = false;
        Boolean isHostHeaderOk = false;
        Boolean isAdditonalHeader1Ok = false;
        Boolean isAdditonalHeader2Ok = false;
        Boolean isAdditonalHeader3Ok = false;

        Scanner scanner = new Scanner(actual);

        int lineCount = 0;
        while (scanner.hasNextLine()) {
            lineCount++;

            String line = scanner.nextLine();
            if (line.equals("GET https://" + hostName + "/" + webSocketPath + queryKey + queryValue + " HTTP/1.1")) {
                isStatusLineOk = true;
                continue;
            }
            if (line.equals("Connection: Upgrade,Keep-Alive")) {
                isConnectionHeaderOk = true;
                continue;
            }
            if (line.equals("Upgrade: websocket")) {
                isUpgradeHeaderOk = true;
                continue;
            }
            if (line.equals("Sec-WebSocket-Version: 13")) {
                isWebSocketVersionHeaderOk = true;
                continue;
            }
            if (line.startsWith("Sec-WebSocket-Key: ")) {
                String keyBase64 = line.substring(19);
                if (keyBase64.length() == 24) {
                    byte[] decoded = Base64.decodeBase64Local(keyBase64.getBytes());
                    if (decoded.length == 16) {
                        isWebSocketKeyHeaderOk = true;
                    }
                }

                continue;
            }
            if (line.equals("Sec-WebSocket-Protocol: " + webSocketProtocol)) {
                isWebSocketProtocolHeaderOk = true;
                continue;
            }
            if (line.equals("Host: host_XXX")) {
                isHostHeaderOk = true;
                continue;
            }
            if (line.equals("header1: content1")) {
                isAdditonalHeader1Ok = true;
                continue;
            }
            if (line.equals("header2: content2")) {
                isAdditonalHeader2Ok = true;
                continue;
            }
            if (line.equals("header3: content3")) {
                isAdditonalHeader3Ok = true;
                continue;
            }
        }
        if (lineCount == 11) {
            isLineCountOk = true;
        }

        assertTrue(isLineCountOk);
        assertTrue(isStatusLineOk);
        assertTrue(isUpgradeHeaderOk);
        assertTrue(isConnectionHeaderOk);
        assertTrue(isWebSocketVersionHeaderOk);
        assertTrue(isWebSocketKeyHeaderOk);
        assertTrue(isWebSocketProtocolHeaderOk);
        assertTrue(isHostHeaderOk);
        assertTrue(isAdditonalHeader1Ok);
        assertTrue(isAdditonalHeader2Ok);
        assertTrue(isAdditonalHeader3Ok);
    }

    @Test
    public void testCreateUpgradeRequest_no_additonal_headers() {
        String hostName = "host_XXX";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";
        String queryKey = "?iothub-no-client-cert=";
        String queryValue = "true";

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath, queryKey + queryValue, webSocketPort, webSocketProtocol, null);

        String actual = webSocketUpgrade.createUpgradeRequest();

        Boolean isLineCountOk = false;
        Boolean isStatusLineOk = false;
        Boolean isUpgradeHeaderOk = false;
        Boolean isConnectionHeaderOk = false;
        Boolean isWebSocketVersionHeaderOk = false;
        Boolean isWebSocketKeyHeaderOk = false;
        Boolean isWebSocketProtocolHeaderOk = false;
        Boolean isHostHeaderOk = false;

        Scanner scanner = new Scanner(actual);

        int lineCount = 0;
        while (scanner.hasNextLine()) {
            lineCount++;

            String line = scanner.nextLine();
            if (line.equals("GET https://" + hostName + "/" + webSocketPath + queryKey + queryValue + " HTTP/1.1")) {
                isStatusLineOk = true;
                continue;
            }
            if (line.equals("Connection: Upgrade,Keep-Alive")) {
                isConnectionHeaderOk = true;
                continue;
            }
            if (line.equals("Upgrade: websocket")) {
                isUpgradeHeaderOk = true;
                continue;
            }
            if (line.equals("Sec-WebSocket-Version: 13")) {
                isWebSocketVersionHeaderOk = true;
                continue;
            }
            if (line.startsWith("Sec-WebSocket-Key: ")) {
                String keyBase64 = line.substring(19);
                if (keyBase64.length() == 24) {
                    byte[] decoded = Base64.decodeBase64Local(keyBase64.getBytes());
                    if (decoded.length == 16) {
                        isWebSocketKeyHeaderOk = true;
                    }
                }
                continue;
            }
            if (line.equals("Sec-WebSocket-Protocol: " + webSocketProtocol)) {
                isWebSocketProtocolHeaderOk = true;
                continue;
            }
            if (line.equals("Host: host_XXX")) {
                isHostHeaderOk = true;
                continue;
            }
        }
        if (lineCount == 8) {
            isLineCountOk = true;
        }

        assertTrue(isLineCountOk);
        assertTrue(isStatusLineOk);
        assertTrue(isUpgradeHeaderOk);
        assertTrue(isConnectionHeaderOk);
        assertTrue(isWebSocketVersionHeaderOk);
        assertTrue(isWebSocketKeyHeaderOk);
        assertTrue(isWebSocketProtocolHeaderOk);
        assertTrue(isHostHeaderOk);
    }

    @Test
    public void testCreateUpgradeRequest_path_start_with_slash() {
        String hostName = "host_XXX";
        String webSocketPath = "/path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";
        String queryKey = "?iothub-no-client-cert=";
        String queryValue = "true";

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath,queryKey + queryValue, webSocketPort, webSocketProtocol, null);

        String actual = webSocketUpgrade.createUpgradeRequest();

        Boolean isLineCountOk = false;
        Boolean isStatusLineOk = false;
        Boolean isUpgradeHeaderOk = false;
        Boolean isConnectionHeaderOk = false;
        Boolean isWebSocketVersionHeaderOk = false;
        Boolean isWebSocketKeyHeaderOk = false;
        Boolean isWebSocketProtocolHeaderOk = false;
        Boolean isHostHeaderOk = false;

        Scanner scanner = new Scanner(actual);

        int lineCount = 0;
        while (scanner.hasNextLine()) {
            lineCount++;

            String line = scanner.nextLine();
            if (line.equals("GET https://" + hostName + webSocketPath + queryKey + queryValue + " HTTP/1.1")) {
                isStatusLineOk = true;
                continue;
            }
            if (line.equals("Connection: Upgrade,Keep-Alive")) {
                isConnectionHeaderOk = true;
                continue;
            }
            if (line.equals("Upgrade: websocket")) {
                isUpgradeHeaderOk = true;
                continue;
            }
            if (line.equals("Sec-WebSocket-Version: 13")) {
                isWebSocketVersionHeaderOk = true;
                continue;
            }
            if (line.startsWith("Sec-WebSocket-Key: ")) {
                String keyBase64 = line.substring(19);
                if (keyBase64.length() == 24) {
                    byte[] decoded = Base64.decodeBase64Local(keyBase64.getBytes());
                    if (decoded.length == 16) {
                        isWebSocketKeyHeaderOk = true;
                    }
                }
                continue;
            }
            if (line.equals("Sec-WebSocket-Protocol: " + webSocketProtocol)) {
                isWebSocketProtocolHeaderOk = true;
                continue;
            }
            if (line.equals("Host: host_XXX")) {
                isHostHeaderOk = true;
                continue;
            }
        }
        if (lineCount == 8) {
            isLineCountOk = true;
        }

        assertTrue(isLineCountOk);
        assertTrue(isStatusLineOk);
        assertTrue(isUpgradeHeaderOk);
        assertTrue(isConnectionHeaderOk);
        assertTrue(isWebSocketVersionHeaderOk);
        assertTrue(isWebSocketKeyHeaderOk);
        assertTrue(isWebSocketProtocolHeaderOk);
        assertTrue(isHostHeaderOk);
    }

    @Test(expected = InvalidParameterException.class)
    public void testCreateUpgradeRequest_empty_host() {
        String hostName = "";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";
        Map<String, String> additionalHeaders = new HashMap<String, String>();
        additionalHeaders.put("header1", "content1");
        additionalHeaders.put("header2", "content2");
        additionalHeaders.put("header3", "content3");

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath,"", webSocketPort, webSocketProtocol, additionalHeaders);
        webSocketUpgrade.createUpgradeRequest();
    }

    @Test(expected = InvalidParameterException.class)
    public void testCreateUpgradeRequest_empty_protocol() {
        String hostName = "host_XXX";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "";
        Map<String, String> additionalHeaders = new HashMap<String, String>();
        additionalHeaders.put("header1", "content1");
        additionalHeaders.put("header2", "content2");
        additionalHeaders.put("header3", "content3");

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath, "", webSocketPort, webSocketProtocol, additionalHeaders);
        webSocketUpgrade.createUpgradeRequest();
    }

    @Test
    public void testvalidateUpgradeReply() {
        String hostName = "host_XXX";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath, "", webSocketPort, webSocketProtocol, null);
        String upgradeRequest = webSocketUpgrade.createUpgradeRequest();
        String keyBase64 = upgradeRequest.substring(upgradeRequest.lastIndexOf("Sec-WebSocket-Key: ") + 19, upgradeRequest.lastIndexOf("Sec-WebSocket-Key: ") + 43);

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            String serverKey = Base64.encodeBase64StringLocal(messageDigest.digest((keyBase64 + RFC_GUID).getBytes())).trim();

            String responseStr = String.join("\n", "HTTP/1.1 101 Switching Protocols",
                "Upgrade: websocket",
                "Server: XXXYYYZZZ",
                "Sec-WebSocket-Protocol: " + webSocketProtocol,
                "Connection: Upgrade",
                "Sec-WebSocket-Accept: " + serverKey,
                "Date: Thu, 03 Mar 2016 22:46:15 GMT","Sec-WebSocket-Protocol: \" + webSocketProtocol" + "\n");

            assertTrue(webSocketUpgrade.validateUpgradeReply(responseStr.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            assertTrue(false);
        }
    }

    @Test
    public void testvalidateUpgradeReply_invalid_key() {
        String hostName = "host_XXX";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath, "", webSocketPort, webSocketProtocol, null);
        String upgradeRequest = webSocketUpgrade.createUpgradeRequest();
        String keyBase64 = upgradeRequest.substring(upgradeRequest.lastIndexOf("Sec-WebSocket-Key: ") + 19, upgradeRequest.lastIndexOf("Sec-WebSocket-Key: ") + 43);
        // Generate new key
        webSocketUpgrade.createUpgradeRequest();

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            String serverKey = Base64.encodeBase64StringLocal(messageDigest.digest((keyBase64 + RFC_GUID).getBytes())).trim();

            String responseStr = String.join("\n", "HTTP/1.1 101 Switching Protocols",
                "Upgrade: websocket",
                "Server: XXXYYYZZZ",
                "Sec-WebSocket-Protocol: " + webSocketProtocol,
                "Connection: Upgrade",
                "Sec-WebSocket-Accept: " + serverKey,
                "Date: Thu, 03 Mar 2016 22:46:15 GMT");

            assertFalse(webSocketUpgrade.validateUpgradeReply(responseStr.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            assertTrue(false);
        }
    }

    @Test
    public void testvalidateUpgradeReply_plain_key() {
        String hostName = "host_XXX";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath, "", webSocketPort, webSocketProtocol, null);
        String serverKey = "ABCDEFGHIJKLM123";

        String responseStr = String.join("\n", "HTTP/1.1 101 Switching Protocols",
            "Upgrade: websocket",
            "Server: XXXYYYZZZ",
            "Sec-WebSocket-Protocol: " + webSocketProtocol,
            "Connection: Upgrade",
            "Sec-WebSocket-Accept: " + serverKey,
            "Date: Thu, 03 Mar 2016 22:46:15 GMT");

        assertFalse(webSocketUpgrade.validateUpgradeReply(responseStr.getBytes()));
    }

    @Test
    public void testvalidateUpgradeReply_missing_status_line() {
        String hostName = "host_XXX";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath, "", webSocketPort, webSocketProtocol, null);
        String upgradeRequest = webSocketUpgrade.createUpgradeRequest();
        String keyBase64 = upgradeRequest.substring(upgradeRequest.lastIndexOf("Sec-WebSocket-Key: ") + 19, upgradeRequest.lastIndexOf("Sec-WebSocket-Key: ") + 43);

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            String serverKey = Base64.encodeBase64StringLocal(messageDigest.digest((keyBase64 + RFC_GUID).getBytes())).trim();
            String responseStr = String.join("\n", "Upgrade: websocket",
                "Server: XXXYYYZZZ",
                "Sec-WebSocket-Protocol: " + webSocketProtocol,
                "Connection: Upgrade",
                "Sec-WebSocket-Accept: " + serverKey,
                "Date: Thu, 03 Mar 2016 22:46:15 GMT");

            assertFalse(webSocketUpgrade.validateUpgradeReply(responseStr.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            assertTrue(false);
        }
    }

    @Test
    public void testvalidateUpgradeReply_missing_upgrade_header() {
        String hostName = "host_XXX";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath, "", webSocketPort, webSocketProtocol, null);
        String upgradeRequest = webSocketUpgrade.createUpgradeRequest();
        String keyBase64 = upgradeRequest.substring(upgradeRequest.lastIndexOf("Sec-WebSocket-Key: ") + 19, upgradeRequest.lastIndexOf("Sec-WebSocket-Key: ") + 43);

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            String serverKey = Base64.encodeBase64StringLocal(messageDigest.digest((keyBase64 + RFC_GUID).getBytes())).trim();

            String responseStr = String.join("\n", "HTTP/1.1 101 Switching Protocols",
                "Server: XXXYYYZZZ",
                "Sec-WebSocket-Protocol: " + webSocketProtocol,
                "Connection: Upgrade",
                "Sec-WebSocket-Accept: " + serverKey,
                "Date: Thu, 03 Mar 2016 22:46:15 GMT");

            assertFalse(webSocketUpgrade.validateUpgradeReply(responseStr.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            assertTrue(false);
        }
    }

    @Test
    public void testvalidateUpgradeReply_missing_protocol_header() {
        String hostName = "host_XXX";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath, "", webSocketPort, webSocketProtocol, null);
        String upgradeRequest = webSocketUpgrade.createUpgradeRequest();
        String keyBase64 = upgradeRequest.substring(upgradeRequest.lastIndexOf("Sec-WebSocket-Key: ") + 19, upgradeRequest.lastIndexOf("Sec-WebSocket-Key: ") + 43);

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            String serverKey = Base64.encodeBase64StringLocal(messageDigest.digest((keyBase64 + RFC_GUID).getBytes())).trim();
            String responseStr = String.join("\n", "HTTP/1.1 101 Switching Protocols",
                "Upgrade: websocket",
                "Server: XXXYYYZZZ",
                "Connection: Upgrade",
                "Sec-WebSocket-Accept: " + serverKey,
                "Date: Thu, 03 Mar 2016 22:46:15 GMT");

            assertFalse(webSocketUpgrade.validateUpgradeReply(responseStr.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            assertTrue(false);
        }
    }

    @Test
    public void testvalidateUpgradeReply_missing_connection_header() {
        String hostName = "host_XXX";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath, "", webSocketPort, webSocketProtocol, null);
        String upgradeRequest = webSocketUpgrade.createUpgradeRequest();
        String keyBase64 = upgradeRequest.substring(upgradeRequest.lastIndexOf("Sec-WebSocket-Key: ") + 19, upgradeRequest.lastIndexOf("Sec-WebSocket-Key: ") + 43);

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            String serverKey = Base64.encodeBase64StringLocal(messageDigest.digest((keyBase64 + RFC_GUID).getBytes())).trim();
            String responseStr = String.join("\n", "HTTP/1.1 101 Switching Protocols",
                "Upgrade: websocket",
                "Server: XXXYYYZZZ",
                "Sec-WebSocket-Protocol: " + webSocketProtocol,
                "Sec-WebSocket-Accept: " + serverKey,
                "Date: Thu, 03 Mar 2016 22:46:15 GMT");

            assertFalse(webSocketUpgrade.validateUpgradeReply(responseStr.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            assertTrue(false);
        }
    }

    @Test
    public void testvalidateUpgradeReply_missing_accept_header() {
        String hostName = "host_XXX";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath, "", webSocketPort, webSocketProtocol, null);
        String responseStr = String.join("\n", "HTTP/1.1 101 Switching Protocols",
            "Upgrade: websocket",
            "Server: XXXYYYZZZ",
            "Sec-WebSocket-Protocol: " + webSocketProtocol,
            "Connection: Upgrade",
            "Date: Thu, 03 Mar 2016 22:46:15 GMT");

        assertFalse(webSocketUpgrade.validateUpgradeReply(responseStr.getBytes()));
    }

    @Test
    public void testvalidateUpgradeReply_emptyResponse() {
        String hostName = "host_XXX";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";
        Map<String, String> additionalHeaders = new HashMap<String, String>();
        additionalHeaders.put("header1", "content1");
        additionalHeaders.put("header2", "content2");
        additionalHeaders.put("header3", "content3");

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath, "", webSocketPort, webSocketProtocol, additionalHeaders);

        String responseStr = "";

        assertFalse(webSocketUpgrade.validateUpgradeReply(responseStr.getBytes()));
    }

    @Test
    public void testToString() {
        String hostName = "host_XXX";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";
        Map<String, String> additionalHeaders = new HashMap<String, String>();
        additionalHeaders.put("header1", "content1");
        additionalHeaders.put("header2", "content2");
        additionalHeaders.put("header3", "content3");

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath, "", webSocketPort, webSocketProtocol, additionalHeaders);
        webSocketUpgrade.createUpgradeRequest();

        String actual = webSocketUpgrade.toString();


        String expexted1 = "WebSocketUpgrade [host=" + hostName
            + ", path=/" + webSocketPath
            + ", port=" + webSocketPort
            + ", protocol=" + webSocketProtocol
            + ", webSocketKey=";

        String expected2 = ", additionalHeaders=header3:content3, header2:content2, header1:content1]";

        assertTrue(actual.startsWith(expexted1));
        actual = actual.substring(expexted1.length() + 24);
        assertTrue(actual.equals(expected2));
    }

    @Test
    public void testToString_no_additional_headers() {
        String hostName = "host_XXX";
        String webSocketPath = "path1/path2";
        int webSocketPort = 1234567890;
        String webSocketProtocol = "subprotocol_name";

        WebSocketUpgrade webSocketUpgrade = new WebSocketUpgrade(hostName, webSocketPath, "", webSocketPort, webSocketProtocol, null);
        String expected = String.join("", "WebSocketUpgrade [host=" + hostName,
            ", path=/" + webSocketPath,
            ", port=" + webSocketPort,
            ", protocol=" + webSocketProtocol,
            ", webSocketKey=]");

        String actual = webSocketUpgrade.toString();

        assertEquals("Unexpected value for toString()", expected, actual);
    }
}
