package com.example.coinrain.core

/**
 * Parses a euro amount string ("2.43") into an integer cent value (243).
 *
 * Format: optional integer part, optional "." followed by 1 or 2 decimal digits.
 * "2.43" → 243, "1" → 100, "0.50" → 50.
 * Negative values and amounts above [MAX_AMOUNT_CENTS] throw [PromoAmountParseException].
 */
object AmountParser {

    const val MAX_AMOUNT_CENTS = 999_99  // 999.99 €

    @Throws(PromoAmountParseException::class)
    fun parse(amountStr: String): Int {
        val s = amountStr.trim()
        if (s.isEmpty()) throw PromoAmountParseException("Amount string is empty")

        val dotIdx = s.indexOf('.')
        return try {
            if (dotIdx < 0) {
                val euros = s.toLong()
                if (euros < 0) throw PromoAmountParseException("Amount must be >= 0: '$amountStr'")
                val cents = euros * 100
                if (cents > MAX_AMOUNT_CENTS) throw PromoAmountParseException("Amount too large: '$amountStr'")
                cents.toInt()
            } else {
                val eurosStr = if (dotIdx == 0) "0" else s.substring(0, dotIdx)
                val fracStr  = s.substring(dotIdx + 1)
                if (fracStr.length > 2) throw PromoAmountParseException("Too many decimal places: '$amountStr'")
                val euros = eurosStr.toLong()
                if (euros < 0) throw PromoAmountParseException("Amount must be >= 0: '$amountStr'")
                val centsFrac = when (fracStr.length) {
                    0    -> 0
                    1    -> fracStr.toInt() * 10
                    else -> fracStr.toInt()
                }
                val total = euros * 100 + centsFrac
                if (total > MAX_AMOUNT_CENTS) throw PromoAmountParseException("Amount too large: '$amountStr'")
                total.toInt()
            }
        } catch (e: NumberFormatException) {
            throw PromoAmountParseException("Cannot parse '$amountStr' as a monetary amount")
        }
    }
}
