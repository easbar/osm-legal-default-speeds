package de.westnordost.osm_default_speeds.tagfilter.filters

import kotlin.test.*

class NotHasKeyTest {
    val c = NotHasKey("name")

    @Test fun matches() {
        assertFalse(c.matches(mapOf("name" to "yes")))
        assertFalse(c.matches(mapOf("name" to "no")))
        assertTrue(c.matches(mapOf("neme" to "no")))
        assertTrue(c.matches(mapOf()))
    }

    @Test fun toStringMethod() {
        assertEquals("!name", c.toString())
    }
}