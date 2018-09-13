/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.proton.transport.proxy.impl;

import com.microsoft.azure.proton.transport.proxy.ProxyHandler;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class ProxyHandlerImplTest {
    @Test
    public void testCreateProxyRequest() {
        final String hostName = "testHostName";
        final HashMap<String, String> headers = new HashMap<>();
        headers.put("header1", "headervalue1");
        headers.put("header2", "headervalue2");

        final ProxyHandlerImpl proxyHandler = new ProxyHandlerImpl();
        final String actualProxyRequest = proxyHandler.createProxyRequest(hostName, headers);

        final String expectedProxyRequest = "CONNECT testHostName HTTP/1.1\r\n" +
                "Host: testHostName\r\n" +
                "Connection: Keep-Alive\r\n" +
                "header2: headervalue2\r\n" +
                "header1: headervalue1\r\n" +
                "\r\n";

        Assert.assertEquals(expectedProxyRequest, actualProxyRequest);
    }

    @Test
    public void testValidateProxyResponseOnSuccess() {
        final String validResponse = "HTTP/1.1 200 Connection Established\r\n" +
                "FiddlerGateway: Direct\r\n" +
                "StartTime: 13:08:21.574\r\n" +
                "Connection: close\r\n\r\n";
        final ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put(validResponse.getBytes(StandardCharsets.UTF_8));
        buffer.flip();

        final ProxyHandlerImpl proxyHandler = new ProxyHandlerImpl();
        ProxyHandler.ProxyResponseResult responseResult = proxyHandler.validateProxyResponse(buffer);

        Assert.assertTrue(responseResult.getIsSuccess());
        Assert.assertNull(responseResult.getError());

        Assert.assertEquals(0, buffer.remaining());
    }

    @Test
    public void testValidateProxyResponseOnFailure() {
        final String failResponse = "HTTP/1.1 407 Proxy Auth Required\r\n" +
                "Connection: close\r\n" +
                "Proxy-Authenticate: Basic realm=\"FiddlerProxy (user: 1, pass: 1)\"\r\n" +
                "Content-Type: text/html\r\n" +
                "<html><body>[Fiddler] Proxy Authentication Required.<BR></body></html>\r\n\r\n";
        final ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put(failResponse.getBytes(StandardCharsets.UTF_8));
        buffer.flip();

        final ProxyHandlerImpl proxyHandler = new ProxyHandlerImpl();
        ProxyHandler.ProxyResponseResult responseResult = proxyHandler.validateProxyResponse(buffer);

        Assert.assertTrue(!responseResult.getIsSuccess());
        Assert.assertEquals(failResponse, responseResult.getError());

        Assert.assertEquals(0, buffer.remaining());
    }

    @Test
    public void testValidateProxyResponseOnInvalidResponse() {
        final String invalidResponse = "HTTP/1.1 abc Connection Established\r\n" +
                "HTTP/1.1 200 Connection Established\r\n" +
                "FiddlerGateway: Direct\r\n" +
                "StartTime: 13:08:21.574\r\n" +
                "Connection: close\r\n\r\n";
        final ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put(invalidResponse.getBytes(StandardCharsets.UTF_8));
        buffer.flip();

        final ProxyHandlerImpl proxyHandler = new ProxyHandlerImpl();
        ProxyHandler.ProxyResponseResult responseResult = proxyHandler.validateProxyResponse(buffer);

        Assert.assertTrue(!responseResult.getIsSuccess());
        Assert.assertEquals(invalidResponse, responseResult.getError());

        Assert.assertEquals(0, buffer.remaining());
    }

    @Test
    public void testValidateProxyResponseOnEmptyResponse() {
        final String emptyResponse = "\r\n\r\n";
        final ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put(emptyResponse.getBytes(StandardCharsets.UTF_8));
        buffer.flip();

        final ProxyHandlerImpl proxyHandler = new ProxyHandlerImpl();
        ProxyHandler.ProxyResponseResult responseResult = proxyHandler.validateProxyResponse(buffer);

        Assert.assertTrue(!responseResult.getIsSuccess());
        Assert.assertEquals(emptyResponse, responseResult.getError());

        Assert.assertEquals(0, buffer.remaining());
    }
}
