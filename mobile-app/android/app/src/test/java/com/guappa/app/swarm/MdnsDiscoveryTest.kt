package com.guappa.app.swarm

import org.junit.Assert.*
import org.junit.Test

class MdnsDiscoveryTest {

    @Test
    fun `service type is correct`() {
        assertEquals("_guappa-swarm._tcp", MdnsDiscovery.SERVICE_TYPE)
    }

    @Test
    fun `discovered connector generates correct URL`() {
        val connector = MdnsDiscovery.DiscoveredConnector(
            name = "test-connector",
            host = "192.168.1.100",
            port = 9371
        )
        assertEquals("http://192.168.1.100:9371", connector.url)
        assertEquals("test-connector", connector.name)
    }

    @Test
    fun `discovered connector with IPv6`() {
        val connector = MdnsDiscovery.DiscoveredConnector(
            name = "ipv6-node",
            host = "::1",
            port = 9371
        )
        assertEquals("http://::1:9371", connector.url)
    }
}
