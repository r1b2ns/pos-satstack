package com.possatstack.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SatsFormatterTest {

    @Test
    fun `zero is returned without grouping`() {
        assertEquals("0", SatsFormatter.format("0"))
    }

    @Test
    fun `values below one thousand are not grouped`() {
        assertEquals("1", SatsFormatter.format("1"))
        assertEquals("100", SatsFormatter.format("100"))
        assertEquals("999", SatsFormatter.format("999"))
    }

    @Test
    fun `values with four digits get one grouping separator`() {
        assertEquals("1,000", SatsFormatter.format("1000"))
        assertEquals("5,449", SatsFormatter.format("5449"))
        assertEquals("15,000", SatsFormatter.format("15000"))
    }

    @Test
    fun `large values use comma as thousands separator`() {
        assertEquals("325,000,000", SatsFormatter.format("325000000"))
        assertEquals("100,000,000", SatsFormatter.format("100000000"))
    }

    @Test
    fun `invalid input is returned unchanged`() {
        assertEquals("", SatsFormatter.format(""))
        assertEquals("abc", SatsFormatter.format("abc"))
        assertEquals("1.5", SatsFormatter.format("1.5"))
    }
}
