package com.example.netprofile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    private val good = Snapshot(
        airplaneMode = false,
        batteryOptimized = false,
        dataSaverOn = false,
        privateDns = PrivateDns.AUTO,
        wifiEnabled = true,
    )

    private fun List<Check>.byId(id: String) = first { it.id == id }

    @Test
    fun share_allGood_hasNoProblems() {
        val checks = Rules.evaluate(Role.SHARE, good)
        assertTrue(checks.filter { it.id in setOf("airplane", "battery", "data_saver", "private_dns") }
            .all { it.status == Status.OK })
    }

    @Test
    fun share_airplaneOn_isBad_andPreselected() {
        val c = Rules.evaluate(Role.SHARE, good.copy(airplaneMode = true)).byId("airplane")
        assertEquals(Status.BAD, c.status)
        assertTrue(c.preselected)
    }

    @Test
    fun share_batteryOptimized_isWarn_withExemptionAction() {
        val c = Rules.evaluate(Role.SHARE, good.copy(batteryOptimized = true)).byId("battery")
        assertEquals(Status.WARN, c.status)
        assertEquals(Apply.BatteryExemption, c.apply)
        assertTrue(c.preselected)
    }

    @Test
    fun share_dataSaverOn_isWarn() {
        val c = Rules.evaluate(Role.SHARE, good.copy(dataSaverOn = true)).byId("data_saver")
        assertEquals(Status.WARN, c.status)
    }

    @Test
    fun share_privateDnsHostname_isWarn() {
        val c = Rules.evaluate(Role.SHARE, good.copy(privateDns = PrivateDns.HOSTNAME)).byId("private_dns")
        assertEquals(Status.WARN, c.status)
    }

    @Test
    fun share_manualHotspotItems_areNotPreselected() {
        val checks = Rules.evaluate(Role.SHARE, good)
        listOf("hotspot_band", "hotspot_security", "hotspot_autoff").forEach {
            val c = checks.byId(it)
            assertEquals(Status.INFO, c.status)
            assertFalse(c.preselected)
        }
    }

    @Test
    fun receive_wifiOff_isBad() {
        val c = Rules.evaluate(Role.RECEIVE, good.copy(wifiEnabled = false)).byId("wifi")
        assertEquals(Status.BAD, c.status)
        assertTrue(c.preselected)
    }

    @Test
    fun receive_doesNotContainShareOnlyChecks() {
        val ids = Rules.evaluate(Role.RECEIVE, good).map { it.id }
        assertFalse(ids.contains("hotspot_band"))
        assertFalse(ids.contains("hotspot_autoff"))
    }

    @Test
    fun privateDnsUnknown_isInfo_notPreselected() {
        val c = Rules.evaluate(Role.SHARE, good.copy(privateDns = PrivateDns.UNKNOWN)).byId("private_dns")
        assertEquals(Status.INFO, c.status)
        assertFalse(c.preselected)
    }
}
