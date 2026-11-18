package io.eksoda.engine.fetch

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import java.net.InetAddress

class PublicAddressTest {
    private fun public(address: String) = isPublicAddress(InetAddress.getByName(address))

    @Test
    fun `only globally routable IPv4 addresses are public`() {
        mapOf(
            "8.8.8.8" to true,
            "172.15.255.255" to true,
            "192.0.0.9" to true, // a global exception inside 192.0.0.0/24
            "10.0.0.1" to false,
            "100.64.0.1" to false,
            "127.0.0.1" to false,
            "169.254.1.1" to false,
            "172.16.0.1" to false,
            "172.31.255.255" to false,
            "192.168.1.1" to false,
            "198.51.100.7" to false,
            "224.0.0.1" to false,
            "255.255.255.255" to false,
            "0.0.0.0" to false,
        ).forEach { (address, expected) ->
            assertThat(public(address), name = address).isEqualTo(expected)
        }
    }

    @Test
    fun `only globally routable IPv6 addresses are public`() {
        mapOf(
            "2606:4700:4700::1111" to true,
            "2001:4:112::1" to true, // a global exception inside 2001::/23
            "::1" to false,
            "::" to false,
            "fe80::1" to false,
            "fc00::1" to false,
            "ff02::1" to false,
            "2001:db8::1" to false,
            "64:ff9b:1::1" to false,
            "::ffff:10.0.0.1" to false, // judged as the IPv4 address it reaches
            "::ffff:8.8.8.8" to true,
        ).forEach { (address, expected) ->
            assertThat(public(address), name = address).isEqualTo(expected)
        }
    }
}
