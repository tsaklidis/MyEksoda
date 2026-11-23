package io.eksoda.engine.analytics

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class GoalsTest {
    private fun decimal(text: String) = BigDecimal(text)

    // --- E-payments --------------------------------------------------------------------------

    @Test
    fun `the target is 30 percent of the income, at most 20000`() {
        assertThat(ePaymentTarget(decimal("18000")).toPlainString()).isEqualTo("5400.00")
        assertThat(ePaymentTarget(decimal("12345.67")).toPlainString()).isEqualTo("3703.70")
        assertThat(ePaymentTarget(decimal("66666.67")).toPlainString()).isEqualTo("20000.00")
        assertThat(ePaymentTarget(decimal("200000")).toPlainString()).isEqualTo("20000.00")
        assertThat(ePaymentTarget(decimal("0")).toPlainString()).isEqualTo("0.00")
        assertThat(ePaymentTarget(decimal("-10")).toPlainString()).isEqualTo("0.00")
    }

    @Test
    fun `only card and bank payments of a split receipt are electronic`() {
        val parts = listOf(
            PaymentPart("card", decimal("12.50")),
            PaymentPart("cash", decimal("7.50")),
            PaymentPart("bank", decimal("1.00")),
            PaymentPart(null, decimal("3.00")),
        )
        assertThat(electronicPart(parts).toPlainString()).isEqualTo("13.50")
        assertThat(electronicPart(emptyList()).signum()).isEqualTo(0)
    }

    @Test
    fun `a split receipt's stored payments give its electronic part, negative for a refund`() {
        val json = """
            {"payments": [
              {"amount": "12.50", "method": "card"},
              {"amount": "7.50", "method": "cash"},
              {"amount": 1.00, "method": "bank"},
              {"amount": "oops", "method": "card"},
              {"amount": null, "method": "card"},
              {"amount": "2.00", "method": null},
              "not an object"
            ]}
        """.trimIndent()
        assertThat(electronicPartOf(json, creditNote = false).toPlainString()).isEqualTo("13.50")
        assertThat(electronicPartOf(json, creditNote = true).toPlainString()).isEqualTo("-13.50")
    }

    @Test
    fun `stored data that cannot be read counts nothing`() {
        for (json in listOf("", "{", "[]", "{}", """{"payments": 3}""")) {
            assertThat(electronicPartOf(json, creditNote = false).signum()).isEqualTo(0)
        }
    }

    @Test
    fun `the pace spreads what is left over the months left, this one included`() {
        // April 15, 2026: 9 months left (April to December), day 105 of 365.
        val pace = ePaymentPace(decimal("5400.00"), decimal("1260.00"), LocalDate.of(2026, 4, 15))
        assertThat(pace.remaining.toPlainString()).isEqualTo("4140.00")
        assertThat(pace.perMonth.toPlainString()).isEqualTo("460.00")
        assertThat(pace.projected.toPlainString()).isEqualTo("4380.00")
        assertThat(pace.paidPercent?.toPlainString()).isEqualTo("23.33")
    }

    @Test
    fun `a reached target leaves nothing, and a zero target has no percent`() {
        val done = ePaymentPace(decimal("1000"), decimal("1500"), LocalDate.of(2026, 12, 31))
        assertThat(done.remaining.toPlainString()).isEqualTo("0.00")
        assertThat(done.perMonth.toPlainString()).isEqualTo("0.00")
        assertThat(done.projected.toPlainString()).isEqualTo("1500.00")
        assertThat(done.paidPercent?.toPlainString()).isEqualTo("150.00")
        assertThat(ePaymentPace(decimal("0"), decimal("10"), LocalDate.of(2026, 1, 1)).paidPercent)
            .isNull()
    }

    // --- Budgets -----------------------------------------------------------------------------

    @Test
    fun `a budget is under, near from 80 percent, and over once passed`() {
        val under = budgetUse(decimal("300"), decimal("120.50"))
        assertThat(under.level).isEqualTo(BudgetLevel.UNDER)
        assertThat(under.left.toPlainString()).isEqualTo("179.50")
        assertThat(under.usedPercent?.toPlainString()).isEqualTo("40.17")

        assertThat(budgetUse(decimal("300"), decimal("240")).level).isEqualTo(BudgetLevel.NEAR)
        assertThat(budgetUse(decimal("300"), decimal("300")).level).isEqualTo(BudgetLevel.NEAR)

        val over = budgetUse(decimal("300"), decimal("312.40"))
        assertThat(over.level).isEqualTo(BudgetLevel.OVER)
        assertThat(over.left.toPlainString()).isEqualTo("-12.40")
    }

    @Test
    fun `refunds below zero and a zero budget are handled`() {
        assertThat(budgetUse(decimal("100"), decimal("-5")).level).isEqualTo(BudgetLevel.UNDER)
        val zero = budgetUse(decimal("0"), decimal("1"))
        assertThat(zero.usedPercent).isNull()
        assertThat(zero.level).isEqualTo(BudgetLevel.OVER)
        assertThat(budgetUse(decimal("0"), decimal("0")).level).isEqualTo(BudgetLevel.UNDER)
    }

    @Test
    fun `the month's elapsed share counts today`() {
        assertThat(monthElapsedPercent(LocalDate.of(2026, 2, 7)).toPlainString()).isEqualTo("25.00")
        assertThat(monthElapsedPercent(LocalDate.of(2026, 11, 30)).toPlainString())
            .isEqualTo("100.00")
    }
}
