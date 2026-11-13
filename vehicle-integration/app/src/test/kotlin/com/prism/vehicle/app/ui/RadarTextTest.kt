package com.prism.vehicle.app.ui

import com.prism.vehicle.replay.Point2
import com.prism.vehicle.replay.RiskFrame
import com.prism.vehicle.replay.RiskLevel
import com.prism.vehicle.replay.RiskObject
import com.prism.vehicle.replay.RiskZone
import com.prism.vehicle.replay.Snapshot
import com.prism.vehicle.replay.VehicleFrame
import com.prism.vehicle.replay.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadarTextTest {
    private fun car(id: String, x: Double, y: Double, vy: Double) = VehicleState(id, x, y, 0.0, vy, Math.PI / 2, 4.6, 1.8)
    private val ego = car("ego", 0.0, 0.0, 20.0)
    private val zone = listOf(Point2(0.0, 0.0), Point2(1.0, 0.0), Point2(1.0, 1.0))

    private fun snap(risk: RiskFrame?, stale: Boolean = false) = Snapshot(
        tMs = 1000, vehicle = VehicleFrame("1.0", 1000, null, "ego", listOf(ego, car("car_02", -3.6, 10.0, 22.0), car("car_01", 0.0, 30.0, 15.0))),
        prediction = null, risk = risk, stale = stale, frameAgeMs = 0,
    )

    @Test fun horizonLevels_onlyFromRiskZones() {
        val risk = RiskFrame("1.0", 1000, listOf(
            RiskObject("car_02", RiskLevel.WARNING, listOf("LANE_CHANGE_INTRUSION"), riskZones = listOf(RiskZone(1000, zone))),
            RiskObject("car_01", RiskLevel.WATCH, listOf("FRONT_CLOSING"), riskZones = listOf(RiskZone(3000, zone))),
        ))
        // 5초 영역은 02 가 내지 않았으므로 05 가 채우지 않는다
        assertEquals(listOf(RiskLevel.WARNING, RiskLevel.WARNING, RiskLevel.WATCH, null), RadarText.horizonLevels(snap(risk)))
    }

    @Test fun noRiskFrame_noLevels_andHonestHeadline() {
        assertEquals(listOf<RiskLevel?>(null, null, null, null), RadarText.horizonLevels(snap(null)))
        assertEquals("02 위험 엔진 결과가 없는 시나리오입니다.", RadarText.headline(snap(null)))
    }

    @Test fun headline_usesSideOfTopObject() {
        val risk = RiskFrame("1.0", 1000, listOf(RiskObject("car_02", RiskLevel.WARNING, listOf("LANE_CHANGE_INTRUSION"))))
        assertEquals("좌측 차량이 자차 차선으로 끼어들 가능성이 있습니다.", RadarText.headline(snap(risk)))
    }

    @Test fun frontRelative_sameLaneNearestAhead() {
        val (v, rel) = RadarText.frontRelative(snap(null).vehicle!!.vehicles, "ego", 3.6)!!
        assertEquals("car_01", v.id)
        assertEquals(-18, rel) // (15 - 20) m/s
        assertEquals(72, RadarText.speedKmh(ego))
        assertNull(RadarText.frontRelative(listOf(ego), "ego", 3.6))
    }
}
