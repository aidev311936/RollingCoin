package com.example.coinrain.core

/**
 * Minimum-coin decomposition for Euro amounts.
 *
 * Algorithm: standard DP coin-change over the allowed denominations. If the
 * amount is not exactly representable (e.g. {50c,20c} cannot make 243c because
 * 243 is not divisible by 10), the largest representable amount ≤ target is
 * covered by the DP result and the remainder is filled with CENT_1 coins.
 *
 * CENT_1 is therefore always guaranteed to cover any gap, so every non-negative
 * integer cent amount is representable. No "not representable" error can occur.
 */
object CoinChange {

    fun decompose(amountCents: Int, allowed: Set<Denomination>): List<Denomination> {
        require(amountCents >= 0) { "amountCents must be >= 0, got $amountCents" }
        if (amountCents == 0) return emptyList()

        val sorted = allowed.sortedByDescending { it.cents }

        val INF = Int.MAX_VALUE / 2
        val dp   = IntArray(amountCents + 1) { INF }
        val from = arrayOfNulls<Denomination>(amountCents + 1)
        dp[0] = 0

        for (i in 1..amountCents) {
            for (d in sorted) {
                if (d.cents <= i && dp[i - d.cents] != INF && dp[i - d.cents] + 1 < dp[i]) {
                    dp[i] = dp[i - d.cents] + 1
                    from[i] = d
                }
            }
        }

        val result = mutableListOf<Denomination>()

        if (dp[amountCents] < INF) {
            var rem = amountCents
            while (rem > 0) {
                val d = from[rem]!!
                result += d
                rem -= d.cents
            }
        } else {
            // Not representable with allowed set; cover as much as possible, fill rest with 1c.
            var k = amountCents - 1
            while (k > 0 && dp[k] >= INF) k--
            var rem = k
            while (rem > 0) {
                val d = from[rem]!!
                result += d
                rem -= d.cents
            }
            repeat(amountCents - k) { result += Denomination.CENT_1 }
        }

        return result
    }
}
