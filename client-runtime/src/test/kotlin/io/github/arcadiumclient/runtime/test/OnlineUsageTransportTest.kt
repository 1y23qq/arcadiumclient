// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.prepareGet
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import io.github.arcadiumclient.runtime.client.presence.acceptedUsageResponse

class OnlineUsageTransportTest {
    @Test fun `bounded response accepts a complete 4096 byte response`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(200, 4096)
            exchange.responseBody.use { it.write(ByteArray(4096)) }
        }
        server.start()
        val client = HttpClient(CIO)
        try {
            val accepted = withTimeout(3000) {
                client.prepareGet("http://127.0.0.1:${server.address.port}/").execute { acceptedUsageResponse(it) }
            }
            assertTrue(accepted)
        } finally { client.close(); server.stop(0) }
    }

    @Test fun `chunked response stops after 4097 bytes without waiting for its end`() = runBlocking {
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            try {
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write(ByteArray(4097))
                exchange.responseBody.flush()
                release.await()
            } finally { exchange.close() }
        }
        server.start()
        val client = HttpClient(CIO)
        try {
            val accepted = withTimeout(3000) {
                client.prepareGet("http://127.0.0.1:${server.address.port}/").execute { acceptedUsageResponse(it) }
            }
            assertFalse(accepted)
            assertEquals(1L, release.count)
        } finally { release.countDown(); client.close(); server.stop(0) }
    }
}
