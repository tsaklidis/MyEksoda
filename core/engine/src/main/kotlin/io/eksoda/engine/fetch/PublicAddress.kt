// Port of is_public_address in backend/apps/receipts/fetching.py, with CPython 3.13's
// ipaddress network tables.
package io.eksoda.engine.fetch

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

private class Network(cidr: String) {
    val address: ByteArray = InetAddress.getByName(cidr.substringBefore('/')).address
    val prefix: Int = cidr.substringAfter('/').toInt()

    fun contains(candidate: ByteArray): Boolean {
        if (candidate.size != address.size) return false
        val wholeBytes = prefix / Byte.SIZE_BITS
        if ((0 until wholeBytes).any { candidate[it] != address[it] }) return false
        val remainingBits = prefix % Byte.SIZE_BITS
        if (remainingBits == 0) return true
        val mask = (BYTE_MASK shl (Byte.SIZE_BITS - remainingBits)) and BYTE_MASK
        return (candidate[wholeBytes].toInt() and mask) == (address[wholeBytes].toInt() and mask)
    }
}

private fun networks(vararg cidrs: String) = cidrs.map(::Network)

// IPv4Address: _private_networks (and their global exceptions), 100.64.0.0/10 (not global),
// multicast, reserved, loopback, link-local and unspecified.
private val IPV4_NOT_PUBLIC = networks(
    "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16", "172.16.0.0/12",
    "192.0.0.0/24", "192.0.2.0/24", "192.168.0.0/16", "198.18.0.0/15", "198.51.100.0/24",
    "203.0.113.0/24", "224.0.0.0/4", "240.0.0.0/4", "255.255.255.255/32",
)
private val IPV4_GLOBAL_EXCEPTIONS = networks("192.0.0.9/32", "192.0.0.10/32")

// IPv6Address: _private_networks (and their global exceptions), multicast, the reserved
// ranges, loopback, link-local and unspecified.
private val IPV6_NOT_PUBLIC = networks(
    "::1/128", "::/128", "::ffff:0:0/96", "64:ff9b:1::/48", "100::/64", "2001::/23",
    "2001:db8::/32", "2002::/16", "3fff::/20", "fc00::/7", "fe80::/10", "ff00::/8",
    "::/8", "100::/8", "200::/7", "400::/6", "800::/5", "1000::/4", "4000::/3", "6000::/3",
    "8000::/3", "a000::/3", "c000::/3", "e000::/4", "f000::/5", "f800::/6", "fe00::/9",
)
private val IPV6_GLOBAL_EXCEPTIONS = networks(
    "2001:1::1/128",
    "2001:1::2/128",
    "2001:3::/32",
    "2001:4:112::/48",
    "2001:20::/28",
    "2001:30::/28",
)
private val IPV4_MAPPED = Network("::ffff:0:0/96")
private const val IPV4_BYTES = 4
private const val BYTE_MASK = 0xFF

/** True only for globally routable unicast addresses. */
public fun isPublicAddress(address: InetAddress): Boolean {
    var bytes = address.address
    // "::ffff:10.0.0.1" reaches 10.0.0.1: judge the embedded IPv4 address.
    if (address is Inet6Address &&
        IPV4_MAPPED.contains(bytes)
    ) {
        bytes = bytes.copyOfRange(bytes.size - IPV4_BYTES, bytes.size)
    }
    return when (bytes.size) {
        IPV4_BYTES -> IPV4_GLOBAL_EXCEPTIONS.any { it.contains(bytes) } ||
            IPV4_NOT_PUBLIC.none { it.contains(bytes) }

        else ->
            address !is Inet4Address &&
                (
                    IPV6_GLOBAL_EXCEPTIONS.any { it.contains(bytes) } ||
                        IPV6_NOT_PUBLIC.none { it.contains(bytes) }
                    )
    }
}
