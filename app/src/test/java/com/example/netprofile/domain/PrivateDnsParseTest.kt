package com.example.netprofile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivateDnsParseTest {

    @Test
    fun parsesKnownModes() {
        assertEquals(PrivateDns.OFF, Rules.parsePrivateDns("off"))
        assertEquals(PrivateDns.AUTO, Rules.parsePrivateDns("opportunistic"))
        assertEquals(PrivateDns.HOSTNAME, Rules.parsePrivateDns("hostname"))
    }

    @Test
    fun unsetMeansAuto() {
        assertEquals(PrivateDns.AUTO, Rules.parsePrivateDns(null))
        assertEquals(PrivateDns.AUTO, Rules.parsePrivateDns(""))
    }

    @Test
    fun unknownValueIsUnknown() {
        assertEquals(PrivateDns.UNKNOWN, Rules.parsePrivateDns("something-new"))
    }
}
