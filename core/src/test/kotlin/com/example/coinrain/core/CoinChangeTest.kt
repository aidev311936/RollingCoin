package com.example.coinrain.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoinChangeTest {

    private val allDenoms = Denomination.values().toSet()

    // Convenience: sum cents of a result list
    private fun List<Denomination>.totalCents() = sumOf { it.cents }

    @Test
    fun `243 cents with full denomination set yields 5 coins EURO2 plus 2x20c plus 2c plus 1c`() {
        val result = CoinChange.decompose(243, allDenoms)
        assertEquals(5, result.size)
        assertEquals(243, result.totalCents())
        assertEquals(1, result.count { it == Denomination.EURO_2 })
        assertEquals(2, result.count { it == Denomination.CENT_20 })
        assertEquals(1, result.count { it == Denomination.CENT_2 })
        assertEquals(1, result.count { it == Denomination.CENT_1 })
    }

    @Test
    fun `243 cents without EURO2 and CENT2 yields minimum coins summing to 243`() {
        val allowed = allDenoms - setOf(Denomination.EURO_2, Denomination.CENT_2)
        val result = CoinChange.decompose(243, allowed)
        assertEquals(243, result.totalCents())
        assertFalse("EURO_2 must not appear", result.contains(Denomination.EURO_2))
        assertFalse("CENT_2 must not appear", result.contains(Denomination.CENT_2))
        // Optimal: 2×EURO_1 + 2×CENT_20 + 3×CENT_1 = 7 coins
        assertEquals(7, result.size)
        assertEquals(2, result.count { it == Denomination.EURO_1 })
        assertEquals(2, result.count { it == Denomination.CENT_20 })
        assertEquals(3, result.count { it == Denomination.CENT_1 })
    }

    @Test
    fun `100 cents without EURO1 uses multiple smaller coins not a single euro`() {
        val allowed = allDenoms - setOf(Denomination.EURO_1)
        val result = CoinChange.decompose(100, allowed)
        assertEquals(100, result.totalCents())
        assertFalse("EURO_1 must not appear", result.contains(Denomination.EURO_1))
        assertTrue("Must be more than 1 coin", result.size > 1)
    }

    @Test
    fun `243 cents with only 50c and 20c fills remainder with 1c fallback`() {
        val allowed = setOf(Denomination.CENT_50, Denomination.CENT_20)
        val result = CoinChange.decompose(243, allowed)
        assertEquals(243, result.totalCents())
        // Max representable with {50,20}: 240 = 4×50 + 2×20 (6 coins)
        // Remainder 3 → 3×CENT_1. Total 9 coins.
        assertEquals(4, result.count { it == Denomination.CENT_50 })
        assertEquals(2, result.count { it == Denomination.CENT_20 })
        assertEquals(3, result.count { it == Denomination.CENT_1 })
        assertEquals(9, result.size)
    }

    @Test
    fun `zero cents yields empty list`() {
        assertEquals(emptyList<Denomination>(), CoinChange.decompose(0, allDenoms))
    }

    @Test
    fun `empty allowed set falls back entirely to CENT1`() {
        val result = CoinChange.decompose(5, emptySet())
        assertEquals(5, result.totalCents())
        assertTrue(result.all { it == Denomination.CENT_1 })
    }

    @Test
    fun `amount string 2dot43 parses to 243 cents`() {
        assertEquals(243, AmountParser.parse("2.43"))
    }

    @Test
    fun `amount string integer parses correctly`() {
        assertEquals(100, AmountParser.parse("1"))
        assertEquals(0,   AmountParser.parse("0"))
        assertEquals(200, AmountParser.parse("2"))
    }

    @Test
    fun `amount string single decimal digit parses correctly`() {
        assertEquals(50, AmountParser.parse("0.5"))
        assertEquals(10, AmountParser.parse("0.1"))
    }

    @Test(expected = PromoAmountParseException::class)
    fun `non-numeric string throws PromoAmountParseException`() {
        AmountParser.parse("abc")
    }

    @Test(expected = PromoAmountParseException::class)
    fun `negative amount throws PromoAmountParseException`() {
        AmountParser.parse("-1.00")
    }

    @Test(expected = PromoAmountParseException::class)
    fun `empty string throws PromoAmountParseException`() {
        AmountParser.parse("")
    }

    @Test(expected = PromoAmountParseException::class)
    fun `too many decimal places throws PromoAmountParseException`() {
        AmountParser.parse("1.234")
    }
}
