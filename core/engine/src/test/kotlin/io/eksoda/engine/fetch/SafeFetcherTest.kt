package io.eksoda.engine.fetch

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import io.eksoda.engine.BlockedDestination
import io.eksoda.engine.FormatChanged
import io.eksoda.engine.ProviderHttpError
import io.eksoda.engine.ProviderTimeout
import io.eksoda.engine.ProviderUnreachable
import io.eksoda.engine.ReceiptNotFound
import io.eksoda.engine.ResponseTooLarge
import io.eksoda.engine.UnexpectedContentType
import io.eksoda.engine.url.ParsedUrl
import io.eksoda.engine.url.parseSubmittedUrl
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLException

/** The fetcher's rules, with responses made up by an interceptor: no network. */
class SafeFetcherTest {
    private val host = "receipts.example.gr"
    private val url = parseSubmittedUrl("https://$host/r/1?k=v")
    private val requests = mutableListOf<Request>()
    private val io: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() {
        io.close()
    }

    /** Each call moves the clock by [stepMillis], so politeness waits are short. */
    private fun fetcher(
        maxBytes: Long = FetchLimits().maxBytes,
        stepMillis: Long = 2_000,
        respond: (Request) -> Response,
    ): SafeFetcher {
        var now = 0L
        return SafeFetcher(
            limits = FetchLimits(maxBytes = maxBytes),
            dns = Dns.SYSTEM,
            clock = { now.also { now += TimeUnit.MILLISECONDS.toNanos(stepMillis) } },
            ioDispatcher = io,
            interceptor = { chain ->
                requests += chain.request()
                respond(chain.request())
            },
        )
    }

    private fun response(
        request: Request,
        code: Int = 200,
        body: String = "<html>ok</html>",
        contentType: String? = "text/html; charset=utf-8",
        contentLength: String? = null,
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("status")
        .apply { if (contentType != null) header("Content-Type", contentType) }
        .apply { if (contentLength != null) header("Content-Length", contentLength) }
        .body(body.toByteArray(Charsets.ISO_8859_1).toResponseBody())
        .build()

    private fun SafeFetcher.fetch(
        target: ParsedUrl = url,
        acceptStatuses: Set<Int> = emptySet(),
        contentTypes: Set<String> = HTML_TYPES,
    ): FetchedDocument = runBlocking { get(target, setOf(host), acceptStatuses, contentTypes) }

    @Test
    fun `a page is fetched with fixed headers only`() {
        val document = fetcher { response(it) }.fetch()
        assertThat(document.text()).isEqualTo("<html>ok</html>")
        assertThat(document.status).isEqualTo(200)
        val request = requests.single()
        assertThat(request.url.toString()).isEqualTo("https://$host/r/1?k=v")
        assertThat(request.header("User-Agent")).isEqualTo(USER_AGENT)
        assertThat(request.header("Accept")).isEqualTo("text/html")
        assertThat(request.header("Accept-Language")).isEqualTo("el-GR,el;q=0.9")
        assertThat(request.header("Cookie")).isNull()
        assertThat(request.header("Authorization")).isNull()
    }

    @Test
    fun `the charset comes from the content type, else utf-8`() {
        fun charset(contentType: String) =
            fetcher { response(it, contentType = contentType) }.fetch().charset
        assertThat(charset("text/html; charset=ISO-8859-7")).isEqualTo("iso-8859-7")
        assertThat(charset("text/html; charset=\"UTF-8\"")).isEqualTo("utf-8")
        assertThat(charset("text/html; boundary=x")).isEqualTo("utf-8")
        assertThat(charset("text/html")).isEqualTo("utf-8")
        assertThat(charset("TEXT/HTML ;charset=windows-1253")).isEqualTo("windows-1253")
    }

    @Test
    fun `other content types are refused unless the provider asks for them`() {
        assertFailure { fetcher { response(it, contentType = "application/json") }.fetch() }
            .isInstanceOf<UnexpectedContentType>()
        assertFailure { fetcher { response(it, contentType = null) }.fetch() }
            .isInstanceOf<UnexpectedContentType>()
        val xml = fetcher { response(it, contentType = "application/xml") }
            .fetch(contentTypes = XML_TYPES)
        assertThat(xml.status).isEqualTo(200)
        assertThat(requests.last().header("Accept")).isEqualTo("application/xml, text/xml")
    }

    @Test
    fun `statuses map to errors, and accepted ones are returned`() {
        fun status(code: Int) = fetcher { response(it, code = code) }
        assertFailure { status(404).fetch() }.isInstanceOf<ReceiptNotFound>()
        listOf(408, 429, 500, 503).forEach { code ->
            assertFailure { status(code).fetch() }.isInstanceOf<ProviderHttpError>()
        }
        // Redirects are never followed.
        assertFailure { status(302).fetch() }.isInstanceOf<FormatChanged>()
        assertFailure { status(418).fetch() }.isInstanceOf<FormatChanged>()
        assertThat(status(418).fetch(acceptStatuses = setOf(418)).status).isEqualTo(418)
    }

    @Test
    fun `bodies over the limit are refused, declared or not`() {
        assertFailure { fetcher(maxBytes = 10) { response(it, contentLength = "11") }.fetch() }
            .isInstanceOf<ResponseTooLarge>()
        assertFailure { fetcher(maxBytes = 10) { response(it, body = "x".repeat(11)) }.fetch() }
            .isInstanceOf<ResponseTooLarge>()
        // A malformed length is not trusted; the body is still capped while reading.
        val small = fetcher(maxBytes = 10) { response(it, body = "tiny", contentLength = "n/a") }
        assertThat(small.fetch().text()).isEqualTo("tiny")
    }

    @Test
    fun `only https on port 443 at an allowed host is fetched`() {
        val fetcher = fetcher { response(it) }
        assertFailure { fetcher.fetch(ParsedUrl("http", host, null, "/", "")) }
            .isInstanceOf<BlockedDestination>()
        assertFailure { fetcher.fetch(ParsedUrl("https", host, 8443, "/", "")) }
            .isInstanceOf<BlockedDestination>()
        assertFailure { fetcher.fetch(parseSubmittedUrl("https://other.example.gr/")) }
            .isInstanceOf<BlockedDestination>()
        assertThat(requests.size).isEqualTo(0)
        assertThat(fetcher.fetch(ParsedUrl("https", host, 443, "/", "")).status).isEqualTo(200)
    }

    @Test
    fun `connection failures become provider errors`() {
        fun failing(exception: IOException) = fetcher { throw exception }
        assertFailure { failing(SSLException("handshake")).fetch() }
            .isInstanceOf<ProviderUnreachable>()
        assertFailure { failing(InterruptedIOException("timeout")).fetch() }
            .isInstanceOf<ProviderTimeout>()
        assertFailure { failing(IOException("reset")).fetch() }
            .isInstanceOf<ProviderUnreachable>()
    }

    @Test
    fun `requests to one host wait for the previous one`() {
        // The clock moves 999 ms per reading, so the second request waits about 1 ms.
        val fetcher = fetcher(stepMillis = 999) { response(it) }
        fetcher.fetch()
        fetcher.fetch()
        assertThat(requests.size).isEqualTo(2)
        // Politeness state is kept only for hosts in recent use: at 2 s per clock reading,
        // every earlier host is past its interval and dropped.
        val spaced = fetcher { response(it) }
        for (name in listOf("a", "b", "c")) {
            val other = "$name.example.gr"
            runBlocking { spaced.get(parseSubmittedUrl("https://$other/r"), setOf(other)) }
        }
        assertThat(requests.size).isEqualTo(5)
        assertThat(spaced.trackedHosts).isEqualTo(1)
        // A slot in use is never dropped: while a request to A is in flight (past its interval
        // by then), a request to B cleans up but keeps A's slot.
        val aInFlight = CountDownLatch(1)
        val bDone = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val now = AtomicLong()
        val concurrent = SafeFetcher(
            limits = FetchLimits(),
            dns = Dns.SYSTEM,
            clock = { now.getAndAdd(TimeUnit.SECONDS.toNanos(2)) },
            ioDispatcher = pool,
            interceptor = { chain ->
                if (chain.request().url.host == "a.example.gr") {
                    aInFlight.countDown()
                    check(bDone.await(5, TimeUnit.SECONDS)) { "B never finished" }
                }
                response(chain.request())
            },
        )
        pool.use {
            runBlocking {
                suspend fun fetch(name: String) = concurrent.get(
                    parseSubmittedUrl("https://$name.example.gr/r"),
                    setOf("$name.example.gr"),
                )
                val a = async(pool) { fetch("a") }
                check(aInFlight.await(5, TimeUnit.SECONDS)) { "A never started" }
                fetch("b")
                assertThat(concurrent.trackedHosts).isEqualTo(2)
                bDone.countDown()
                a.await()
            }
        }
    }

    // --- DNS ----------------------------------------------------------------------------------

    private fun dns(vararg addresses: String) =
        PublicDns { _ -> addresses.map(InetAddress::getByName) }

    @Test
    fun `every DNS answer must be public, IPv4 first`() {
        assertThat(dns("2606:4700:4700::1111", "1.1.1.1").lookup(host).map { it.hostAddress })
            .containsExactly("1.1.1.1", "2606:4700:4700:0:0:0:0:1111")
        assertFailure { dns("1.1.1.1", "10.0.0.1").lookup(host) }
            .isInstanceOf<BlockedDestination>()
        assertFailure { dns().lookup(host) }.isInstanceOf<ProviderUnreachable>()
        assertFailure { PublicDns { throw UnknownHostException(it) }.lookup(host) }
            .isInstanceOf<ProviderUnreachable>()
        // Through a whole fetch too (retried, never refused: no answer is not proof that the
        // host does not exist). No interceptor: OkHttp asks the DNS, which fails before any
        // connection is made.
        val fetcher = SafeFetcher(dns = { throw UnknownHostException(it) }, ioDispatcher = io)
        assertFailure { runBlocking { fetcher.get(url, setOf(host)) } }
            .isInstanceOf<ProviderUnreachable>()
    }

    // --- Decoding -----------------------------------------------------------------------------

    @Test
    fun `the body is decoded with its charset`() {
        val greek = "Απόδειξη"
        val encoded = greek.toByteArray(charset("ISO-8859-7"))
        assertThat(FetchedDocument(encoded, charset = "iso-8859-7").text()).isEqualTo(greek)
        assertThat(FetchedDocument(greek.toByteArray(), charset = "").text()).isEqualTo(greek)
        assertFailure { FetchedDocument(ByteArray(0), charset = "x-no-such-charset").text() }
            .isInstanceOf<FormatChanged>()
        assertFailure { FetchedDocument(ByteArray(0), charset = "bad name!").text() }
            .isInstanceOf<FormatChanged>()
    }
}
