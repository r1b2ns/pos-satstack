package com.possatstack.app.util

import java.util.Locale

object SatsFormatter {

    /**
     * Formats a satoshi amount (given as a raw digit string) with grouping
     * separators (e.g. "5449" -> "5,449", "325000000" -> "325,000,000").
     *
     * Returns the input unchanged if it is not a valid integer.
     */
    fun format(rawSats: String): String {
        val value = rawSats.toLongOrNull() ?: return rawSats
        return String.format(Locale.US, "%,d", value)
    }
}
