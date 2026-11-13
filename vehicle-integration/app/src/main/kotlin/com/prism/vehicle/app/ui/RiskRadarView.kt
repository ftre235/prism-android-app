package com.prism.vehicle.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.vehicle.replay.PredictionFrame
import com.prism.vehicle.replay.RiskFrame
import com.prism.vehicle.replay.RiskLevel
import com.prism.vehicle.replay.RiskObject
import com.prism.vehicle.replay.Snapshot
import com.prism.vehicle.replay.VehicleState
import kotlin.math.cos
import kotlin.math.hypot

/*
 * RISK RADAR 원근(운전자 시점) 화면 — 개인 브랜치 시안.
 * 05 는 위험을 계산하지 않는다: 등급·원인·위험 영역은 RiskFrame(02), 궤적은 PredictionFrame(01),
 * 차량 위치·속도는 VehicleFrame(03) 값을 그대로 그린다. 데이터에 없는 제한속도·도로명·날씨는 표시하지 않는다.
 * 좌표: 자차 기준 x=오른쪽+, y=전방+. 예측·위험 영역 좌표도 같은 시점의 자차 좌표계라 변환 없이 그린다.
 */

val RadarBg = Color(0xFF0B1020)
val RadarPanel = Color(0xFF151B2E)
val RadarAccent = Color(0xFF7C6CF0)
private val WatchColor = Color(0xFFFFC83D)
private val WarnColor = Color(0xFFFF4D5E)
private val SafeColor = Color(0xFF8A93A8)

fun levelColor(level: RiskLevel?): Color = when (level) {
    RiskLevel.WARNING -> WarnColor
    RiskLevel.WATCH -> WatchColor
    RiskLevel.UNKNOWN -> Color(0xFFB0BEC5)
    RiskLevel.NORMAL, null -> SafeColor
}

/** 화면 표시용 순수 함수 모음(단위 테스트 대상). 위험 판단은 하지 않고 02·03 값을 문장으로 옮기기만 한다. */
object RadarText {
    val HORIZONS_MS = listOf(0L, 1000L, 3000L, 5000L)

    fun levelLabel(level: RiskLevel?): String = when (level) {
        RiskLevel.WARNING -> "위험"
        RiskLevel.WATCH -> "주의"
        RiskLevel.NORMAL -> "안정"
        RiskLevel.UNKNOWN -> "판단 보류"
        null -> "–"
    }

    fun rank(level: RiskLevel?): Int = when (level) {
        RiskLevel.WARNING -> 3
        RiskLevel.WATCH -> 2
        RiskLevel.UNKNOWN -> 1
        RiskLevel.NORMAL -> 0
        null -> -1
    }

    fun side(v: VehicleState?): String = when {
        v == null -> "주변"
        v.y < -2.5 -> "후방"
        v.x < -1.0 -> "좌측"
        v.x > 1.0 -> "우측"
        else -> "전방"
    }

    fun reasonText(code: String, side: String): String = when (code) {
        "LANE_CHANGE_INTRUSION" -> "$side 차량이 자차 차선으로 끼어들 가능성"
        "PATH_OVERLAP" -> "예측 경로가 자차 경로와 겹침"
        "FRONT_CLOSING" -> "전방 차량과의 거리가 빠르게 줄어듦"
        "REAR_CLOSING", "REAR_APPROACH" -> "후방 차량이 빠르게 접근"
        "HARD_BRAKING", "SUDDEN_BRAKE" -> "$side 차량 급감속"
        "CHAIN_DECEL" -> "앞쪽 차량들 연쇄 감속"
        "LANE_CHANGE" -> "$side 차량 차선 변경"
        "SENSOR_DROPOUT" -> "관측 누락"
        else -> code
    }

    /** 가장 높은 등급의 위험 객체(NORMAL 제외). */
    fun topObject(risk: RiskFrame?): RiskObject? =
        risk?.riskObjects?.filter { it.riskLevel != RiskLevel.NORMAL }?.maxByOrNull { rank(it.riskLevel) }

    fun headline(snap: Snapshot): String {
        val top = topObject(snap.risk) ?: return when {
            snap.stale -> "최근 데이터가 없어 주변 상황을 판단하지 않습니다."
            snap.risk == null -> "02 위험 엔진 결과가 없는 시나리오입니다."
            else -> "가까운 위험이 없습니다."
        }
        val v = snap.vehicle?.vehicles?.firstOrNull { it.id == top.vehicleId }
        val s = side(v)
        return when (top.reasonCodes.firstOrNull()) {
            "LANE_CHANGE_INTRUSION" -> "$s 차량이 자차 차선으로 끼어들 가능성이 있습니다."
            "PATH_OVERLAP" -> "$s 차량의 예측 경로가 자차 경로와 겹칩니다."
            "FRONT_CLOSING" -> "전방 차량과의 거리가 빠르게 줄어들고 있습니다."
            "REAR_CLOSING", "REAR_APPROACH" -> "후방 차량이 빠르게 접근하고 있습니다."
            "HARD_BRAKING", "SUDDEN_BRAKE" -> "$s 차량이 급감속 중입니다."
            "CHAIN_DECEL" -> "앞쪽 차량들이 연쇄적으로 감속하고 있습니다."
            "LANE_CHANGE" -> "$s 차량이 차선을 바꾸고 있습니다."
            "SENSOR_DROPOUT" -> "$s 차량 관측이 끊겨 판단을 보류합니다."
            null -> "$s ${top.vehicleId} 차량을 확인하세요."
            else -> "$s 차량 위험: ${top.reasonCodes.first()}"
        }
    }

    /**
     * 향후 시점별 단계. 0 은 현재 최고 등급, 그 외는 02 risk_zones 에 그 dt 영역이 있는 객체 중 최고 등급.
     * 02 가 그 시점 영역을 내지 않았으면 null(–). 05 가 미래 위험을 새로 판단하지 않는다.
     */
    fun horizonLevels(snap: Snapshot): List<RiskLevel?> = HORIZONS_MS.map { dt ->
        when {
            dt == 0L -> if (snap.risk == null && !snap.stale) null else snap.topLevel
            snap.risk == null -> null
            else -> snap.risk!!.riskObjects
                .filter { o -> o.riskZones.any { it.dtMs == dt } }
                .maxByOrNull { rank(it.riskLevel) }?.riskLevel
        }
    }

    fun speedKmh(v: VehicleState): Int = Math.round(hypot(v.vx, v.vy) * 3.6).toInt()

    /** 같은 차선(|x| < 반 차선) 앞차의 상대속도 km/h. 음수면 가까워지는 중. 03 VehicleFrame 값의 차이일 뿐이다. */
    fun frontRelative(vehicles: List<VehicleState>, egoId: String, laneWidth: Double): Pair<VehicleState, Int>? {
        val ego = vehicles.firstOrNull { it.id == egoId } ?: return null
        val front = vehicles.filter { it.id != egoId && it.y > 0 && kotlin.math.abs(it.x) < laneWidth / 2 }.minByOrNull { it.y } ?: return null
        return front to Math.round((front.vy - ego.vy) * 3.6).toInt()
    }
}

/** 자차 뒤 위쪽에 둔 가상 카메라. 지면(높이 0)을 화면으로 옮긴다. */
private class Cam(val w: Float, val h: Float) {
    val horizon = h * 0.36f
    val f = h * 0.98f // 자차 뒷면 바닥이 화면 높이 약 82% 에 오도록
    fun z(y: Double) = (y + BACK).toFloat()
    fun pt(x: Double, y: Double, height: Double = 0.0): Offset {
        val z = z(y)
        return Offset(w / 2 + f * x.toFloat() / z, horizon + f * (HEIGHT - height).toFloat() / z)
    }
    companion object { const val BACK = 14.0; const val HEIGHT = 5.5 }
}

@Composable
fun PerspectiveRoad(snap: Snapshot, laneWidth: Double, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    Canvas(modifier.clipToBounds().testTag("perspective_road")) {
        val cam = Cam(size.width, size.height)
        drawSkyAndRoad(cam, laneWidth)
        val frame = snap.vehicle ?: return@Canvas
        val egoId = frame.egoId
        val risk = snap.risk
        // 위험 영역(02) → 궤적(01) → 차량(03) 순서로 그려 차량이 위에 오게 한다
        risk?.riskObjects?.filter { it.riskLevel == RiskLevel.WATCH || it.riskLevel == RiskLevel.WARNING }?.forEach { o ->
            o.riskZones.forEach { z -> drawZone(cam, z.polygon.map { it.x to it.y }, levelColor(o.riskLevel)) }
        }
        risk?.riskObjects?.filter { it.riskLevel == RiskLevel.WATCH || it.riskLevel == RiskLevel.WARNING }?.forEach { o ->
            drawTrajectory(cam, snap.prediction, o.vehicleId, levelColor(o.riskLevel))
        }
        drawEgoLane(cam, laneWidth)
        val others = frame.vehicles.filter { it.id != egoId }.sortedByDescending { it.y } // 먼 차부터
        others.forEach { v ->
            val o = snap.riskOf(v.id)
            val lv = if (snap.stale) RiskLevel.UNKNOWN else o?.riskLevel
            drawCar(cam, v, ego = false, level = lv)
        }
        frame.vehicles.firstOrNull { it.id == egoId }?.let { drawCar(cam, it, ego = true, level = null) }
        // 말풍선: 주의 이상 객체 + 접근 중인 앞차
        val front = RadarText.frontRelative(frame.vehicles, egoId, laneWidth)
        others.forEach { v ->
            val o = snap.riskOf(v.id)
            val warn = !snap.stale && (o?.riskLevel == RiskLevel.WATCH || o?.riskLevel == RiskLevel.WARNING)
            val closing = front?.first?.id == v.id && front.second <= -5
            if (!warn && !closing) return@forEach
            val title = when {
                warn -> o!!.reasonCodes.firstOrNull()?.let { RadarText.reasonText(it, RadarText.side(v)) } ?: "${v.id} 확인"
                else -> "전방 접근"
            }
            val sub = buildList {
                if (closing) add("상대 ${front!!.second} km/h")
                o?.ttcS?.let { add("TTC %.1fs".format(it)) }
                add(v.id)
            }.joinToString(" · ")
            drawCallout(cam, tm, v, title, sub, if (warn) levelColor(o!!.riskLevel) else WarnColor)
        }
        if (snap.stale) drawBanner(tm, "데이터 누락 — 판단 보류")
    }
}

private fun DrawScope.drawSkyAndRoad(cam: Cam, lw: Double) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF141A33), Color(0xFF3A2A5C), Color(0xFF1A1F35)), endY = cam.horizon), size = Size(size.width, cam.horizon))
    // 원경 도시 실루엣(고정 패턴, 장식) + 지평선 빛
    val sky = Path().apply {
        moveTo(0f, cam.horizon)
        var x = 0f; var i = 0
        while (x < size.width) {
            val hgt = size.height * (0.025f + 0.05f * (((i * 37 + 11) % 17) / 17f))
            lineTo(x, cam.horizon - hgt); x += size.width * (0.018f + 0.02f * (((i * 13 + 5) % 7) / 7f)); lineTo(x, cam.horizon - hgt); i++
        }
        lineTo(size.width, cam.horizon); close()
    }
    drawPath(sky, Color(0xFF0A0D18))
    drawRect(Brush.verticalGradient(listOf(Color(0x00FF9E6B), Color(0x33FF9E6B)), startY = cam.horizon - size.height * 0.08f, endY = cam.horizon), topLeft = Offset(0f, cam.horizon - size.height * 0.08f), size = Size(size.width, size.height * 0.08f))
    drawRect(Color(0xFF0F1424), topLeft = Offset(0f, cam.horizon), size = Size(size.width, size.height - cam.horizon))
    val far = 220.0
    val near = -8.5
    val half = lw * 1.5 + 1.0
    val road = Path().apply {
        moveTo(cam.pt(-half, near)); lineTo(cam.pt(-half, far)); lineTo(cam.pt(half, far)); lineTo(cam.pt(half, near)); close()
    }
    drawPath(road, Brush.verticalGradient(listOf(Color(0xFF242A3D), Color(0xFF2E3448)), startY = cam.horizon, endY = size.height))
    // 자차 헤드램프가 비추는 전방 노면
    val beam = Path().apply {
        moveTo(cam.pt(-lw * 0.45, 3.0)); lineTo(cam.pt(-lw * 0.9, 60.0)); lineTo(cam.pt(lw * 0.9, 60.0)); lineTo(cam.pt(lw * 0.45, 3.0)); close()
    }
    drawPath(beam, Brush.verticalGradient(listOf(Color(0x00FFFFFF), Color(0x14FFF4DC)), startY = cam.pt(0.0, 60.0).y, endY = cam.pt(0.0, 3.0).y))
    // 가드레일: 도로 양쪽 바깥, 높이 0.8m
    listOf(-half - 0.4, half + 0.4).forEach { gx ->
        drawLine(Color(0xFF8C94A8), cam.pt(gx, near + 4, 0.8), cam.pt(gx, far, 0.8), strokeWidth = 3f)
        var y = 0.0
        while (y < 120) { drawLine(Color(0xFF5E667A), cam.pt(gx, y, 0.0), cam.pt(gx, y, 0.8), strokeWidth = (30f / cam.z(y)).coerceIn(1f, 4f)); y += 8.0 }
    }
    // 차선: 바깥은 실선, 안쪽은 점선 (3차로, 자차는 가운데 차로로 가정)
    for (i in 0..3) {
        val x = (i - 1.5) * lw
        val edge = i == 0 || i == 3
        if (edge) {
            drawLine(Color(0xFFCFD6E6), cam.pt(x, near), cam.pt(x, far), strokeWidth = 4f)
        } else {
            var y = near
            while (y < far) {
                val a = cam.pt(x, y); val b = cam.pt(x, y + 4.0)
                drawLine(Color(0xFFE6EAF2), a, b, strokeWidth = (12f * 9f / cam.z(y)).coerceIn(1.5f, 10f), cap = StrokeCap.Butt)
                y += 12.0
            }
        }
    }
}

private fun Path.moveTo(o: Offset) = moveTo(o.x, o.y)
private fun Path.lineTo(o: Offset) = lineTo(o.x, o.y)

private fun DrawScope.drawEgoLane(cam: Cam, lw: Double) {
    val p = Path().apply {
        moveTo(cam.pt(-lw / 2 + 0.3, 2.5)); lineTo(cam.pt(-lw / 2 + 0.3, 45.0)); lineTo(cam.pt(lw / 2 - 0.3, 45.0)); lineTo(cam.pt(lw / 2 - 0.3, 2.5)); close()
    }
    drawPath(p, Brush.verticalGradient(listOf(Color(0x003D7BFF), Color(0x553D7BFF)), startY = cam.pt(0.0, 45.0).y, endY = cam.pt(0.0, 2.5).y))
}

private fun DrawScope.drawZone(cam: Cam, poly: List<Pair<Double, Double>>, color: Color) {
    if (poly.size < 3 || poly.all { cam.z(it.second) < 1.5f }) return
    val path = Path().apply {
        poly.forEachIndexed { i, (x, y) -> val o = cam.pt(x, y.coerceAtLeast(-7.0)); if (i == 0) moveTo(o) else lineTo(o) }
        close()
    }
    drawPath(path, color.copy(alpha = 0.22f))
    clipPath(path) {
        val b = path.getBounds()
        var x = b.left - b.height
        while (x < b.right) { drawLine(color.copy(alpha = 0.75f), Offset(x, b.bottom), Offset(x + b.height, b.top), strokeWidth = 3f); x += 14f }
    }
    drawPath(path, color, style = Stroke(width = 2f))
}

private fun DrawScope.drawTrajectory(cam: Cam, pf: PredictionFrame?, id: String, color: Color) {
    val traj = pf?.predictions?.firstOrNull { it.vehicleId == id }?.trajectories?.firstOrNull() ?: return
    val pts = traj.points.sortedBy { it.dtMs }.filter { cam.z(it.y) > 2f }.map { cam.pt(it.x, it.y) }
    if (pts.size < 2) return
    val path = Path().apply { moveTo(pts.first()); pts.drop(1).forEach { lineTo(it) } }
    drawPath(path, color, style = Stroke(width = 6f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
    val a = pts[pts.size - 2]; val b = pts.last()
    val d = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat().coerceAtLeast(1f)
    val ux = (b.x - a.x) / d; val uy = (b.y - a.y) / d
    val head = Path().apply {
        moveTo(b.x + ux * 14, b.y + uy * 14); lineTo(b.x - uy * 10, b.y + ux * 10); lineTo(b.x + uy * 10, b.y - ux * 10); close()
    }
    drawPath(head, color)
}

private const val CAR_H = 1.45

private fun DrawScope.drawCar(cam: Cam, v: VehicleState, ego: Boolean, level: RiskLevel?) {
    val dx = v.lengthM / 2 * cos(v.headingRad) // 차선 변경 중 기울기
    val yr = v.y - v.lengthM / 2
    // 자차 뒤쪽 가까운 차는 화면 아래를 크게 가리므로 그리지 않는다(후방 위험은 말풍선·패널로 표시)
    if (!ego && cam.z(yr) < cam.z(-v.lengthM / 2) - 2f) return
    val hw = v.widthM / 2
    val xr = v.x - dx; val xf = v.x + dx
    fun p(x: Double, dy: Double, h: Double) = cam.pt(x, yr + dy, h)
    val w = p(xr + hw, 0.0, 0.0).x - p(xr - hw, 0.0, 0.0).x
    val paint = when {
        ego -> Color(0xFFF2F5FA)
        level == RiskLevel.UNKNOWN -> Color(0xFF7D8596)
        else -> PAINTS[(v.id.hashCode() and 0x7fffffff) % PAINTS.size] // 차마다 다른 도장색(표시용)
    }
    val dark = paint.shade(0.55f)

    // 1) 바닥 그림자: 가장자리로 흐려지는 타원
    val sc = p(xr, v.lengthM * 0.45, 0.0)
    drawOval(
        Brush.radialGradient(listOf(Color(0x99000000), Color(0x00000000)), center = sc, radius = w * 0.85f),
        topLeft = Offset(sc.x - w * 0.85f, sc.y - w * 0.32f), size = Size(w * 1.7f, w * 0.5f),
    )
    // 2) 타이어(차체 아래 양쪽)
    val tireTop = p(xr, 0.15, 0.32).y; val tireBot = p(xr, 0.15, 0.0).y
    listOf(xr - hw + 0.05, xr + hw - 0.33).forEach { tx ->
        val a = p(tx, 0.15, 0.0)
        drawRoundRect(Color(0xFF0B0D12), topLeft = Offset(a.x, tireTop), size = Size(w * 0.16f, tireBot - tireTop), cornerRadius = CornerRadius(w * 0.03f))
    }
    // 3) 지붕: 뒤에서 앞으로 좁아지고 위쪽이 밝다
    val roofH = 1.48
    val roof = Path().apply {
        moveTo(p(xr - hw * 0.70, 0.55, roofH)); lineTo(p(xr + hw * 0.70, 0.55, roofH))
        lineTo(p(xf + hw * 0.66, 2.5, roofH)); lineTo(p(xf - hw * 0.66, 2.5, roofH)); close()
    }
    drawPath(roof, Brush.verticalGradient(listOf(paint.shade(0.95f), paint.shade(0.75f)), startY = p(xr, 2.5, roofH).y, endY = p(xr, 0.55, roofH).y))
    // 4) 차체 뒷면: 아래 범퍼 → 어깨선 → 좁아지는 C필러
    val body = Path().apply {
        moveTo(p(xr - hw * 0.96, 0.0, 0.28)); lineTo(p(xr - hw, 0.05, 0.95))
        lineTo(p(xr - hw * 0.72, 0.5, roofH)); lineTo(p(xr + hw * 0.72, 0.5, roofH))
        lineTo(p(xr + hw, 0.05, 0.95)); lineTo(p(xr + hw * 0.96, 0.0, 0.28)); close()
    }
    drawPath(body, Brush.verticalGradient(listOf(paint, paint.shade(0.82f), dark), startY = p(xr, 0.5, roofH).y, endY = p(xr, 0.0, 0.28).y))
    // 5) 뒷유리: 어두운 유리 + 대각선 반사광
    val glass = Path().apply {
        moveTo(p(xr - hw * 0.82, 0.12, 1.02)); lineTo(p(xr - hw * 0.66, 0.46, roofH - 0.05))
        lineTo(p(xr + hw * 0.66, 0.46, roofH - 0.05)); lineTo(p(xr + hw * 0.82, 0.12, 1.02)); close()
    }
    drawPath(glass, Brush.verticalGradient(listOf(Color(0xFF3A4560), Color(0xFF0E131E)), startY = p(xr, 0.46, roofH).y, endY = p(xr, 0.12, 1.02).y))
    clipPath(glass) {
        val g = glass.getBounds()
        drawLine(Color(0x33FFFFFF), Offset(g.left + g.width * 0.15f, g.bottom), Offset(g.left + g.width * 0.45f, g.top), strokeWidth = g.width * 0.10f)
        drawLine(Color(0x1AFFFFFF), Offset(g.left + g.width * 0.32f, g.bottom), Offset(g.left + g.width * 0.55f, g.top), strokeWidth = g.width * 0.04f)
    }
    // 6) 일체형 테일램프 + 발광
    val lt = p(xr, 0.06, 0.92).y; val lb = p(xr, 0.06, 0.78).y
    val lx0 = p(xr - hw * 0.97, 0.06, 0.85).x; val lx1 = p(xr + hw * 0.97, 0.06, 0.85).x
    val lampCore = if (ego) Color(0xFFFF2E4A) else Color(0xFFE5213A)
    val glowR = w * (if (ego) 0.42f else 0.32f)
    listOf(lx0 + w * 0.12f, lx1 - w * 0.12f).forEach { cx ->
        val c = Offset(cx, (lt + lb) / 2)
        drawCircle(Brush.radialGradient(listOf(lampCore.copy(alpha = 0.45f), Color.Transparent), center = c, radius = glowR), radius = glowR, center = c)
    }
    drawRoundRect(lampCore.copy(alpha = 0.55f), topLeft = Offset(lx0, lt + (lb - lt) * 0.35f), size = Size(lx1 - lx0, (lb - lt) * 0.3f), cornerRadius = CornerRadius(4f))
    drawRoundRect(lampCore, topLeft = Offset(lx0, lt), size = Size(w * 0.26f, lb - lt), cornerRadius = CornerRadius(w * 0.03f))
    drawRoundRect(lampCore, topLeft = Offset(lx1 - w * 0.26f, lt), size = Size(w * 0.26f, lb - lt), cornerRadius = CornerRadius(w * 0.03f))
    drawRoundRect(Color(0xFFFFB3BC), topLeft = Offset(lx0 + w * 0.03f, lt + (lb - lt) * 0.35f), size = Size(w * 0.18f, (lb - lt) * 0.18f), cornerRadius = CornerRadius(2f))
    drawRoundRect(Color(0xFFFFB3BC), topLeft = Offset(lx1 - w * 0.21f, lt + (lb - lt) * 0.35f), size = Size(w * 0.18f, (lb - lt) * 0.18f), cornerRadius = CornerRadius(2f))
    // 7) 범퍼 그늘 + 번호판
    val bt = p(xr, 0.0, 0.45).y; val bb = p(xr, 0.0, 0.28).y
    drawRect(Color(0x55000000), topLeft = Offset(p(xr - hw * 0.94, 0.0, 0.4).x, bt), size = Size(w * 0.94f, bb - bt))
    val plt = p(xr, 0.02, 0.66).y; val plb = p(xr, 0.02, 0.52).y
    drawRoundRect(Color(0xFFE8ECF2), topLeft = Offset(p(xr, 0.0, 0.6).x - w * 0.15f, plt), size = Size(w * 0.30f, plb - plt), cornerRadius = CornerRadius(2f))

    val top = p(xf, 2.5, roofH).y
    val bottom = p(xr, 0.0, 0.0).y
    val left = p(xr - hw, 0.0, 0.0).x
    if (ego) {
        // 자차: 바닥 파란 발광 + 윤곽
        drawOval(Brush.radialGradient(listOf(Color(0x664D7CFF), Color.Transparent), center = Offset(left + w / 2, bottom), radius = w), topLeft = Offset(left - w * 0.4f, bottom - w * 0.2f), size = Size(w * 1.8f, w * 0.4f))
    }
    if (level == RiskLevel.WATCH || level == RiskLevel.WARNING) {
        val c = levelColor(level)
        val pad = w * 0.18f
        val l = left - pad; val r = left + w + pad; val t = top - pad; val btm = bottom + pad * 0.5f
        val k = w * 0.22f
        val sw = (w * 0.04f).coerceIn(2f, 6f)
        drawRoundRect(Brush.radialGradient(listOf(c.copy(alpha = 0.28f), Color.Transparent), center = Offset((l + r) / 2, (t + btm) / 2), radius = (r - l)), topLeft = Offset(l, t), size = Size(r - l, btm - t), cornerRadius = CornerRadius(8f))
        listOf(
            Offset(l, t) to listOf(Offset(l + k, t), Offset(l, t + k)), Offset(r, t) to listOf(Offset(r - k, t), Offset(r, t + k)),
            Offset(l, btm) to listOf(Offset(l + k, btm), Offset(l, btm - k)), Offset(r, btm) to listOf(Offset(r - k, btm), Offset(r, btm - k)),
        ).forEach { (corner, ends) -> ends.forEach { drawLine(c, corner, it, strokeWidth = sw, cap = StrokeCap.Round) } }
    }
}

/** 표시용 도장색(흰색·은색·회색·남색·검정). 위험과 무관. */
private val PAINTS = listOf(Color(0xFFE6E9EF), Color(0xFFB9C0CC), Color(0xFF7A8292), Color(0xFF3B4A6B), Color(0xFF2A2E36), Color(0xFFD9DDE3))

private fun Color.shade(f: Float): Color = Color(red * f, green * f, blue * f, alpha)

private fun DrawScope.drawCallout(cam: Cam, tm: TextMeasurer, v: VehicleState, title: String, sub: String, color: Color) {
    val anchor = if (v.y < -v.lengthM) Offset(size.width / 2, size.height - 120f) // 그리지 않은 후방 차량은 아래쪽에
    else cam.pt(v.x, v.y + v.lengthM / 2, CAR_H + 0.8)
    val t1 = tm.measure(title, TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold))
    val t2 = tm.measure(sub, TextStyle(color = Color(0xFFCFD6E6), fontSize = 12.sp))
    val w = maxOf(t1.size.width, t2.size.width) + 28f
    val h = t1.size.height + t2.size.height + 18f
    val left = (anchor.x - w / 2).coerceIn(8f, size.width - w - 8f)
    val top = (anchor.y - h - 14f).coerceIn(8f, size.height - h - 8f)
    drawLine(color, Offset(anchor.x, top + h), anchor, strokeWidth = 2f)
    drawRoundRect(Color(0xE6141A2B), topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(10f))
    drawRoundRect(color, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(10f), style = Stroke(2.5f))
    drawText(t1, topLeft = Offset(left + 14f, top + 8f))
    drawText(t2, topLeft = Offset(left + 14f, top + 10f + t1.size.height))
}

private fun DrawScope.drawBanner(tm: TextMeasurer, text: String) {
    val t = tm.measure(text, TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
    val w = t.size.width + 32f
    drawRoundRect(Color(0xCC37474F), topLeft = Offset((size.width - w) / 2, 24f), size = Size(w, t.size.height + 16f), cornerRadius = CornerRadius(10f))
    drawText(t, topLeft = Offset((size.width - w) / 2 + 16f, 32f))
}

/** 하단 범례. */
@Composable
fun RadarLegend(modifier: Modifier = Modifier) {
    Row(
        modifier.background(Color(0xCC151B2E), RoundedCornerShape(14.dp)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text("위험 등급", fontSize = 14.sp)
        listOf("안전" to SafeColor, "주의" to WatchColor, "위험" to WarnColor).forEach { (t, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(c, CircleShape)); Spacer(Modifier.width(6.dp)); androidx.compose.material3.Text(t, fontSize = 14.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 26.dp, height = 12.dp).background(WarnColor.copy(alpha = 0.35f), RoundedCornerShape(3.dp)).border(1.dp, WarnColor, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(6.dp)); androidx.compose.material3.Text("예상 위험 영역 (02)", fontSize = 14.sp)
        }
    }
}

/** 주행(원근) / 분석(04 레이더) 전환. */
@Composable
fun ViewModeToggle(analysis: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.background(Color(0xCC151B2E), RoundedCornerShape(14.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(false to "주행", true to "분석").forEach { (v, label) ->
            val on = v == analysis
            androidx.compose.material3.Text(
                label,
                Modifier.background(if (on) RadarAccent else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onChange(v) }.padding(horizontal = 20.dp, vertical = 8.dp).testTag(if (v) "view_analysis" else "view_drive"),
                fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** 향후 5초 단계 표시 (02 risk_zones 기준). */
@Composable
fun HorizonTimeline(levels: List<RiskLevel?>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(28.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val n = levels.size
                val xs = List(n) { i -> size.width * (0.08f + 0.84f * i / (n - 1)) }
                val cy = size.height / 2
                for (i in 0 until n - 1) {
                    val c = if (levels[i + 1] == null) Color(0xFF3A4258) else levelColor(levels[i + 1])
                    drawLine(c, Offset(xs[i], cy), Offset(xs[i + 1], cy), strokeWidth = 5f, cap = StrokeCap.Round)
                }
                levels.forEachIndexed { i, lv ->
                    drawCircle(if (lv == null) Color(0xFF3A4258) else levelColor(lv), radius = if (i == 0) 11f else 8f, center = Offset(xs[i], cy))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            RadarText.HORIZONS_MS.zip(levels).forEach { (dt, lv) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.Text(if (dt == 0L) "현재" else "${dt / 1000}초", fontSize = 13.sp, color = Color(0xFF9AA3B8))
                    androidx.compose.material3.Text(RadarText.levelLabel(lv), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (lv == null) Color(0xFF6B7489) else levelColor(lv))
                }
            }
        }
    }
}
