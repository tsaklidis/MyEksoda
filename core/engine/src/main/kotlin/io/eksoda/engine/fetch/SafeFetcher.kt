package io.eksoda.engine.fetch

import io.eksoda.engine.BlockedDestination
import io.eksoda.engine.FormatChanged
import io.eksoda.engine.ProviderHttpError
import io.eksoda.engine.ProviderTimeout
import io.eksoda.engine.ProviderUnreachable
import io.eksoda.engine.ReceiptNotFound
import io.eksoda.engine.ResponseTooLarge
import io.eksoda.engine.UnexpectedContentType
import io.eksoda.engine.url.ParsedUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

// SSRF-safe HTTPS fetcher for receipt pages, on OkHttp.
//
// Only reachable through a registered provider. Rules:
//
// 1. https, port 443, host exactly in the provider's allowlist.
// 2. Every DNS answer must be a public (globally routable) address. OkHttp then connects to an
//    address from that vetted list, so no separate IP pinning is needed.
// 3. No redirects; unexpected statuses fail.
// 4. Connect/read timeouts plus an overall deadline (OkHttp's call timeout).
// 5. Body capped *after* decompression; only text/html accepted.
// 6. Fixed request headers: no cookies, credentials or client headers; no proxies, no cache.
// 7. Politeness: one request at a time per host, at least 1 s apart.

private const val HTTPS_PORT = 443
private const val CHUNK_SIZE = 64 * 1024
private const val MIN_INTERVAL_MILLIS = 1_000L
private const val STATUS_OK = 200
private const val STATUS_NOT_FOUND = 404
private const val STATUS_TIMEOUT = 408
private const val STATUS_TOO_MANY = 429
private const val STATUS_SERVER_ERROR = 500

/** The `Dns` rule: resolve, require every answer to be public, prefer IPv4 (resolver order kept). */
internal class PublicDns(private val system: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = try {
            system.lookup(hostname).distinct()
        } catch (exception: UnknownHostException) {
            throw ProviderUnreachable("DNS resolution failed for $hostname").apply {
                initCause(exception)
            }
        }
        if (addresses.isEmpty()) throw ProviderUnreachable("no addresses for $hostname")
        // Every answer must be public, not just the one used: a mixed answer is suspicious.
        if (!addresses.all(
                ::isPublicAddress,
            )
        ) {
            throw BlockedDestination("$hostname resolves to a non-public address")
        }
        return addresses.sortedBy { it !is java.net.Inet4Address }
    }
}

public class SafeFetcher(
    private val limits: FetchLimits = FetchLimits(),
    dns: Dns = Dns.SYSTEM,
    private val clock: () -> Long = System::nanoTime,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Fetcher {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(PublicDns(dns))
        .proxy(Proxy.NO_PROXY)
        .cookieJar(CookieJar.NO_COOKIES)
        .cache(null)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(limits.connectTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(limits.readTimeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(limits.totalTimeoutMillis, TimeUnit.MILLISECONDS)
        .build()

    private val hostLocks = ConcurrentHashMap<String, Mutex>()
    private val lastRequest = ConcurrentHashMap<String, Long>()

    override suspend fun get(
        url: ParsedUrl,
        allowedHosts: Set<String>,
        acceptStatuses: Set<Int>,
    ): FetchedDocument {
        checkTarget(url, allowedHosts)
        val lock = hostLocks.getOrPut(url.host) { Mutex() }
        return lock.withLock {
            lastRequest[url.host]?.let { previous ->
                val waitMillis =
                    MIN_INTERVAL_MILLIS - TimeUnit.NANOSECONDS.toMillis(clock() - previous)
                if (waitMillis > 0) delay(waitMillis)
            }
            try {
                withContext(ioDispatcher) { execute(url, acceptStatuses) }
            } finally {
                lastRequest[url.host] = clock()
            }
        }
    }

    private fun checkTarget(url: ParsedUrl, allowedHosts: Set<String>) {
        if (url.scheme != "https" || (url.port != null && url.port != HTTPS_PORT)) {
            throw BlockedDestination("only https on port 443 is allowed")
        }
        if (url.host !in
            allowedHosts
        ) {
            throw BlockedDestination("host ${url.host} is not allowed for this provider")
        }
    }

    private fun execute(url: ParsedUrl, accept: Set<Int>): FetchedDocument {
        // OkHttp adds Accept-Encoding: gzip itself and decompresses, so the size cap below
        // counts decompressed bytes.
        val request = Request.Builder()
            .url(url.geturl())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html")
            .header("Accept-Language", "el-GR,el;q=0.9")
            .get()
            .build()
        val call: Call = client.newCall(request)
        return try {
            call.execute().use { response -> read(response, accept) }
        } catch (exception: SSLException) {
            throw ProviderUnreachable("TLS handshake with ${url.host} failed").apply {
                initCause(exception)
            }
        } catch (exception: InterruptedIOException) {
            throw ProviderTimeout("timeout talking to ${url.host}").apply { initCause(exception) }
        } catch (exception: IOException) {
            throw ProviderUnreachable("connection to ${url.host} broke").apply {
                initCause(exception)
            }
        }
    }

    private fun read(response: Response, accept: Set<Int>): FetchedDocument {
        val status = response.code
        if (status != STATUS_OK && status !in accept) {
            when {
                status == STATUS_NOT_FOUND -> throw ReceiptNotFound("HTTP 404")

                status == STATUS_TIMEOUT || status == STATUS_TOO_MANY ||
                    status >= STATUS_SERVER_ERROR ->
                    throw ProviderHttpError("HTTP $status")

                else -> throw FormatChanged("unexpected HTTP status $status") // incl. redirects
            }
        }
        val contentType = response.header("Content-Type").orEmpty()
        val mime = contentType.substringBefore(';').trim().lowercase(Locale.ROOT)
        if (mime !=
            "text/html"
        ) {
            throw UnexpectedContentType("content type ${mime.ifEmpty { "missing" }}")
        }
        val declared = response.header("Content-Length").orEmpty().trim()
        if (declared.isNotEmpty() && declared.all { it in '0'..'9' } &&
            declared.toBigInteger() > limits.maxBytes.toBigInteger()
        ) {
            throw ResponseTooLarge("declared length $declared")
        }
        return FetchedDocument(
            body = readCapped(response),
            status = status,
            charset = charset(contentType),
        )
    }

    /** Reads the decompressed body, failing as soon as it passes the size limit. */
    private fun readCapped(response: Response): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(CHUNK_SIZE)
        response.body.byteStream().use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count >
                    limits.maxBytes
                ) {
                    throw ResponseTooLarge("body exceeds the size limit")
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    /** `_charset(params)`: the charset parameter, unquoted and lowercase, else utf-8. */
    private fun charset(contentType: String): String {
        val params = if (';' in contentType) contentType.substringAfter(';') else return "utf-8"
        for (param in params.split(';')) {
            val name = param.trim().substringBefore('=')
            val value = param.trim().substringAfter('=', "")
            if (name.lowercase(Locale.ROOT) == "charset" && value.isNotEmpty()) {
                return value.trim('"', '\'', ' ').lowercase(Locale.ROOT)
            }
        }
        return "utf-8"
    }
}
