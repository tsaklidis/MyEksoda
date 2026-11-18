package io.eksoda.engine

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import org.junit.jupiter.api.Test

class ReceiptExceptionTest {
    private val all: List<Pair<ReceiptException, RetryPolicy>> = listOf(
        InvalidUrl() to RetryPolicy.NONE,
        UnsupportedProvider() to RetryPolicy.NONE,
        UnsupportedUrlFormat() to RetryPolicy.NONE,
        ProviderUnreachable() to RetryPolicy.TRANSIENT,
        ProviderTimeout() to RetryPolicy.TRANSIENT,
        ProviderHttpError() to RetryPolicy.TRANSIENT,
        BlockedDestination() to RetryPolicy.NONE,
        ResponseTooLarge() to RetryPolicy.NONE,
        UnexpectedContentType() to RetryPolicy.NONE,
        ReceiptNotFound() to RetryPolicy.NOT_FOUND,
        FormatChanged() to RetryPolicy.NONE,
        InconsistentSource() to RetryPolicy.NONE,
        InternalError() to RetryPolicy.INTERNAL,
    )

    @Test
    fun `every error has its own code, a retry policy and a public message`() {
        assertThat(all.map { it.first.code }.toSet()).isEqualTo(ErrorCode.entries.toSet())
        all.forEach { (error, retry) ->
            assertThat(error.retry, name = error.code.code).isEqualTo(retry)
            assertThat(error.publicMessage, name = error.code.code).isNotEmpty()
            assertThat(error.message).isEqualTo(error.code.code)
        }
    }

    @Test
    fun `the message carries the detail after the code`() {
        assertThat(ReceiptNotFound("HTTP 404").message).isEqualTo("receipt_not_found: HTTP 404")
        assertThat(ReceiptNotFound("HTTP 404").detail).isEqualTo("HTTP 404")
        assertThat(ErrorCode.PROVIDER_TIMEOUT.toString()).isEqualTo("provider_timeout")
        assertThat(RetryPolicy.NOT_FOUND.toString()).isEqualTo("not_found")
    }
}
