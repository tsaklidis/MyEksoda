package io.eksoda.engine.url

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.endsWith
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import io.eksoda.engine.InvalidUrl
import org.junit.jupiter.api.Test

class ReceiptUrlTest {
    @Test
    fun `a plain https link is split and normalized`() {
        val url = parseSubmittedUrl("  https://Example.COM/a/b?x=1&y=2 ")
        assertThat(url).isEqualTo(ParsedUrl("https", "example.com", null, "/a/b", "x=1&y=2"))
        assertThat(url.target).isEqualTo("/a/b?x=1&y=2")
        assertThat(url.geturl()).isEqualTo("https://example.com/a/b?x=1&y=2")
        assertThat(parseSubmittedUrl("https://example.com").path).isEqualTo("/")
        assertThat(parseSubmittedUrl("https://example.com:443/r").geturl())
            .isEqualTo("https://example.com:443/r")
        assertThat(parseSubmittedUrl("https://example.com:/r").port).isNull()
        assertThat(ParsedUrl("https", "example.com", null, "r", "").geturl())
            .isEqualTo("https://example.com/r")
        assertThat(ParsedUrl("https", "example.com", null, "/r", "").target).isEqualTo("/r")
    }

    @Test
    fun `IP literals and international names are hosts`() {
        assertThat(parseSubmittedUrl("https://8.8.8.8/").host).isEqualTo("8.8.8.8")
        assertThat(parseSubmittedUrl("https://[2001:db8::1]/").host).isEqualTo("2001:db8::1")
        val idn = parseSubmittedUrl("https://παράδειγμα.gr/").host
        assertThat(idn).startsWith("xn--")
        assertThat(idn).endsWith(".gr")
        // A full-width dot is a dot.
        assertThat(parseSubmittedUrl("https://example．com/").host).isEqualTo("example.com")
    }

    @Test
    fun `anything but a plain https link is invalid`() {
        listOf(
            42,
            null,
            "",
            "https://" + "a".repeat(2050) + ".com/",
            "http://example.com/",
            "example.com/r",
            "https://user@example.com/",
            "https://user:pass@example.com/",
            "https://example.com/r#top",
            "https://example.com:8443/",
            "https://example.com:abc/",
            "https://example.com:99999/",
            "https://exa mple.com/",
            "https://example.com/\u0007",
            "https:///r",
            "https://-bad-.com/",
            "https://" + "a".repeat(64) + ".com/",
            "https://xn--παράδειγμα.gr/",
        ).forEach { raw ->
            assertFailure { parseSubmittedUrl(raw) }.isInstanceOf<InvalidUrl>()
        }
    }

    @Test
    fun `malformed brackets fail like urlsplit`() {
        listOf(
            "https://[abc/",
            "https://a]b/",
            "https://[1.2.3.4]/",
            "https://[vz]/",
            "https://x[::1]/",
            "https://[::1]x/",
            "https://example℀.com/",
        ).forEach { raw ->
            assertFailure { parseSubmittedUrl(raw) }.isInstanceOf<IllegalArgumentException>()
        }
    }

    @Test
    fun `IPv4 literals follow ipaddress`() {
        assertThat(isIpv4("1.2.3.4")).isTrue()
        assertThat(isIpv4("0.0.0.0")).isTrue()
        listOf("", "01.2.3.4", "256.1.1.1", "1.2.3", "a.b.c.d", "1..2.3", "1234.1.1.1").forEach {
            assertThat(isIpv4(it), name = it).isFalse()
        }
    }

    @Test
    fun `IPv6 literals follow ipaddress`() {
        val valid = listOf(
            "::",
            "::1",
            "2001:db8::1",
            "1:2:3:4:5:6:7:8",
            "1:2:3:4:5:6:7::",
            "::ffff:1.2.3.4",
            "fe80::1%eth0",
        )
        valid.forEach { assertThat(isIpv6(it), name = it).isTrue() }
        val invalid = listOf(
            "",
            "1:2",
            "1:2:3:4:5:6:7",
            "1:2:3:4:5:6:7:8:9",
            "1::2::3",
            ":1::2",
            "1::2:",
            "::ffff:1.2.3",
            "fe80::1%",
            "fe80::1%a%b",
            "12345::",
            "g::1",
        )
        invalid.forEach { assertThat(isIpv6(it), name = it).isFalse() }
        assertThat(ipVersion("1.2.3.4")).isEqualTo(IpVersion.V4)
        assertThat(ipVersion("::1")).isEqualTo(IpVersion.V6)
        assertThat(ipVersion("example.com")).isNull()
    }
}
