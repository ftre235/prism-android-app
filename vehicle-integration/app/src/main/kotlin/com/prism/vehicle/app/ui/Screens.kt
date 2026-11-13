package com.prism.vehicle.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.vehicle.adapters.SupportStatus
import com.prism.vehicle.app.AppMode
import com.prism.vehicle.app.PrismViewModel
import com.prism.vehicle.app.Screen
import com.prism.vehicle.app.UiState
import com.prism.vehicle.replay.ReplayClock
import com.prism.vehicle.replay.RiskLevel
import com.prism.vehicle.replay.Severity
import com.prism.vehicle.replay.Snapshot

@Composable
fun PrismApp(vm: PrismViewModel) {
    val s by vm.ui.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = RadarBg, contentColor = Color(0xFFE8EAED)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                TopBar(s)
                Spacer(Modifier.height(12.dp))
                when (s.screen) {
                    Screen.SELECT -> SelectScreen(s, vm)
                    Screen.DRIVE -> DriveScreen(s, vm)
                    Screen.REPLAY -> ReplayScreen(s, vm)
                    Screen.ERROR -> ErrorScreen(s, vm)
                }
            }
        }
    }
}

/**
 * 넓은 창(차량 2560px 가로)은 좌우 배치, 좁은 창(분할 화면·휴대폰)은 위아래 배치.
 * 고정 폭 패널이 창보다 넓으면 주 영역 폭이 0 이 되는 문제를 막는다.
 */
@Composable
private fun Adaptive(sideWidth: Dp, main: @Composable (Modifier) -> Unit, side: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= sideWidth * 2) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                main(Modifier.weight(1f).fillMaxHeight())
                side(Modifier.width(sideWidth).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                main(Modifier.fillMaxWidth().weight(1f))
                side(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun TopBar(s: UiState) {
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PRISM", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("RISK RADAR", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RadarAccent)
        Badge(s.mode.label, Color(0xFF37474F))
        // 지시서: 실시간 추론이 아닌 offline replay 임을 화면에 항상 표시
        Badge("시뮬레이션 결과 재생 · 실시간 감지 아님", Color(0xFF4E342E))
        if (s.timeline?.manifest?.mock == true) Badge("MOCK 데이터", Color(0xFF6A1B9A))
        if (s.mode == AppMode.PLEOS_CONTEXT) Badge("차량 상태·위치만 실제 SDK, 주변 차량 위험은 시뮬레이션", Color(0xFF1B5E20))
    }
}

@Composable
private fun Badge(text: String, bg: Color) {
    Text(text, Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 14.sp)
}

@Composable
private fun SelectScreen(s: UiState, vm: PrismViewModel) {
    Adaptive(520.dp, side = { SdkPanel(s, vm, it.verticalScroll(rememberScrollState())) }, main = { mainMod ->
        Column(mainMod) {
            Text("앱 모드", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppMode.entries.forEach { m ->
                    val enabled = m != AppMode.ADAS
                    if (m == s.mode) Button(onClick = {}, enabled = enabled) { Text(m.label) }
                    else OutlinedButton(onClick = { vm.setMode(m) }, enabled = enabled, modifier = Modifier.testTag("mode_${m.name}")) { Text(m.label) }
                }
            }
            Text("ADAS Mode: 공식 접근 권한 확인 전 비활성", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(16.dp))
            Text("시나리오 선택", fontWeight = FontWeight.Bold)
            if (s.scenarioIds.isEmpty()) Text("assets/scenarios/index.json 에 시나리오가 없습니다", color = Color(0xFFE57373))
            LazyColumn(Modifier.weight(1f).testTag("scenario_list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.scenarioIds) { id ->
                    Card(
                        onClick = { vm.openScenario(id) },
                        modifier = Modifier.fillMaxWidth().testTag("scenario_$id"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E232A)),
                    ) { Text(id, Modifier.padding(16.dp), fontSize = 18.sp) }
                }
            }
            if (s.loading) Text("불러오는 중…")
        }
    })
}

@Composable
private fun SdkPanel(s: UiState, vm: PrismViewModel, modifier: Modifier) {
    Column(modifier.background(Color(0xFF161A20), RoundedCornerShape(8.dp)).padding(12.dp)) {
        Text("Pleos SDK 상태", fontWeight = FontWeight.Bold)
        if (s.mode != AppMode.PLEOS_CONTEXT) Text("Simulation Mode 에서는 SDK 를 호출하지 않습니다", fontSize = 13.sp, color = Color.Gray)
        s.sdk.features.forEach { (f, st) ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(st.status.name, Modifier.width(120.dp), color = statusColor(st.status), fontSize = 14.sp)
                Column {
                    Text(f.label, fontSize = 14.sp)
                    if (st.detail.isNotBlank()) Text(st.detail, fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
        val brake = when (s.sdk.brakePressed) { true -> "밟음"; false -> "뗌"; null -> "-" }
        Text("기어: ${s.sdk.gear}  /  브레이크: $brake  /  위치: ${s.sdk.location?.let { "%.5f, %.5f".format(it.latitude, it.longitude) } ?: "-"}", fontSize = 13.sp)
        if (s.mode == AppMode.PLEOS_CONTEXT) OutlinedButton(onClick = { vm.speakTest() }) { Text("TTS 테스트") }
    }
}

private fun statusColor(st: SupportStatus) = when (st) {
    SupportStatus.CONFIRMED -> Color(0xFF81C784)
    SupportStatus.FAILED -> Color(0xFFE57373)
    SupportStatus.UNSUPPORTED -> Color(0xFFFFB74D)
    SupportStatus.UNTESTED, SupportStatus.PENDING -> Color.Gray
}

/**
 * 주행 화면: 왼쪽 메뉴 · 가운데 원근 도로(주행) 또는 04 레이더(분석) · 오른쪽 경고/향후 5초/원인/조작.
 * 주행 중에는 짧은 안내만 두고, 시간 이동·세부 근거는 정차 후 리플레이로 분리한다.
 */
@Composable
private fun DriveScreen(s: UiState, vm: PrismViewModel) {
    val t = s.timeline ?: return
    val snap = t.snapshotAt(s.clock.positionMs)
    var analysis by rememberSaveable { mutableStateOf(false) }
    val laneWidth = if (t.manifest.mock) 3.6 else 4.0
    val view: @Composable (Modifier) -> Unit = { m ->
        Box(m.clip(RoundedCornerShape(18.dp)).background(RadarPanel)) {
            if (analysis) RadarSlot(s, snap, detail = false, modifier = Modifier.fillMaxSize())
            else PerspectiveRoad(snap, laneWidth, Modifier.fillMaxSize())
            snap.vehicle?.vehicles?.firstOrNull { it.id == t.manifest.egoId }?.let { ego ->
                Column(Modifier.align(Alignment.TopStart).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${RadarText.speedKmh(ego)}", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                        Text(" km/h", fontSize = 22.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Text("자차 속도 · 03 시뮬레이션", fontSize = 13.sp, color = Color(0xFFB8C0D4))
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadarLegend()
                ViewModeToggle(analysis, { analysis = it })
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 860.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NavRail(s, vm, Modifier.width(84.dp).fillMaxHeight())
                view(Modifier.weight(1f).fillMaxHeight())
                RiskSidePanel(s, vm, snap, Modifier.width(420.dp).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                view(Modifier.fillMaxWidth().weight(1f))
                RiskSidePanel(s, vm, snap, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun NavRail(s: UiState, vm: PrismViewModel, modifier: Modifier) {
    Column(modifier.background(RadarPanel, RoundedCornerShape(18.dp)).padding(vertical = 12.dp, horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NavItem("실시간", selected = true, enabled = true) {}
        NavItem("리플레이", selected = false, enabled = s.replayBlockedReason == null, onClick = vm::openReplay)
        NavItem("시나리오", selected = false, enabled = true, onClick = vm::back)
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(64.dp)
            .background(if (selected) RadarAccent else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 14.sp, color = if (enabled) Color.White else Color(0xFF5C6479), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun RiskSidePanel(s: UiState, vm: PrismViewModel, snap: Snapshot, modifier: Modifier) {
    val t = s.timeline ?: return
    val level = snap.topLevel
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 경고 카드: 02 RiskFrame 최고 등급과 원인 문장
        val tint = if (snap.risk == null && !snap.stale) Color(0xFF3A4258) else levelColor(level)
        Column(
            Modifier.fillMaxWidth().background(tint.copy(alpha = 0.16f), RoundedCornerShape(18.dp)).border(1.5.dp, tint, RoundedCornerShape(18.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(RadarText.levelLabel(if (snap.risk == null && !snap.stale) null else level), Modifier.background(tint, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp), color = Color(0xFF111522), fontWeight = FontWeight.Bold)
                Text(levelText(snap), Modifier.testTag("level_banner"), fontSize = 15.sp, color = Color(0xFFCFD6E6))
            }
            Text(RadarText.headline(snap), fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
            if (s.mode == AppMode.PLEOS_CONTEXT) OutlinedButton(onClick = { vm.speak(RadarText.headline(snap)) }) { Text("음성 안내") }
        }
        SideCard("위험 예측 (향후 5초)") {
            HorizonTimeline(RadarText.horizonLevels(snap))
            Text(
                if (snap.risk == null) "02 위험 엔진 결과가 없어 예측 단계를 표시하지 않습니다." else "02 위험 영역(risk_zones)의 시점별 등급 · 영역이 없는 시점은 –",
                fontSize = 12.sp, color = Color(0xFF8A93A8),
            )
        }
        SideCard("주요 원인") {
            val objs = snap.risk?.riskObjects.orEmpty().filter { it.riskLevel != RiskLevel.NORMAL }.sortedByDescending { RadarText.rank(it.riskLevel) }
            val vs = snap.vehicle?.vehicles.orEmpty()
            if (objs.isEmpty()) Text(if (snap.risk == null) "02 RiskFrame 없음" else "주의 이상 차량 없음", fontSize = 14.sp, color = Color(0xFF8A93A8))
            objs.forEach { o ->
                val side = RadarText.side(vs.firstOrNull { it.id == o.vehicleId })
                val reasons = o.reasonCodes.joinToString(" · ") { RadarText.reasonText(it, side) }.ifEmpty { "원인 코드 없음" }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(levelColor(o.riskLevel), CircleShape))
                    Text("  ${o.vehicleId} · $reasons${o.ttcS?.let { " · TTC %.1fs".format(it) } ?: ""}", fontSize = 14.sp)
                }
            }
            RadarText.frontRelative(vs, t.manifest.egoId, if (t.manifest.mock) 3.6 else 4.0)?.let { (v, rel) ->
                Text("전방 ${v.id} %.0fm · 상대속도 %+d km/h (03 값)".format(v.y, rel), fontSize = 13.sp, color = Color(0xFF9AA3B8))
            }
        }
        SideCard(t.manifest.title) {
            Text("%.1f / %.1f s".format(s.clock.positionMs / 1000.0, t.durationMs / 1000.0), fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayPause(s.clock, vm)
                OutlinedButton(onClick = vm::back) { Text("시나리오 목록") }
            }
            if (s.mode == AppMode.SIMULATION) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = s.simulatedParked, onCheckedChange = vm::setSimulatedParked, modifier = Modifier.testTag("parked"))
                    Text("  정차(P) 가정 — Simulation Mode 전용", fontSize = 14.sp)
                }
            }
            Button(onClick = vm::openReplay, enabled = s.replayBlockedReason == null, modifier = Modifier.testTag("open_replay")) { Text("정차 후 리플레이") }
            s.replayBlockedReason?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
        }
    }
}

@Composable
private fun SideCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(RadarPanel, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        content()
    }
}

private fun levelText(snap: Snapshot): String {
    val level = snap.topLevel
    return when {
        snap.stale -> "데이터 누락 — 현재 상태 알 수 없음"
        snap.risk == null -> "위험 정보 없음 (RiskFrame 없음)"
        level == RiskLevel.WARNING -> "WARNING · 주변 차량 위험"
        level == RiskLevel.WATCH -> "WATCH · 주의 관찰"
        level == RiskLevel.UNKNOWN -> "UNKNOWN · 판단 불가"
        else -> "NORMAL"
    }
}

@Composable
private fun LevelBanner(snap: Snapshot) {
    val level = snap.topLevel
    val bg = if (level == RiskLevel.UNKNOWN) Color(0xFF263238) else riskColor(level).copy(alpha = 0.35f)
    Box(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(8.dp)).padding(16.dp).testTag("level_banner")) {
        Text(levelText(snap), fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlayPause(clock: ReplayClock, vm: PrismViewModel) {
    if (clock.playing) Button(onClick = vm::pause, modifier = Modifier.testTag("pause")) { Text("일시정지") }
    else Button(onClick = vm::play, modifier = Modifier.testTag("play")) { Text(if (clock.finished) "처음부터" else "재생") }
}

@Composable
private fun ReplayScreen(s: UiState, vm: PrismViewModel) {
    val t = s.timeline ?: return
    val snap = t.snapshotAt(s.clock.positionMs)
    Adaptive(640.dp, main = { RadarSlot(s, snap, detail = true, modifier = it) }) { sideMod ->
        Column(sideMod, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("리플레이 · ${t.manifest.title}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            LevelBanner(snap)
            Slider(
                value = s.clock.positionMs.toFloat(),
                onValueChange = { vm.seek(it.toLong()) },
                valueRange = 0f..t.durationMs.toFloat().coerceAtLeast(1f),
                modifier = Modifier.testTag("seek"),
            )
            Text("%.1f / %.1f s · 프레임 나이 %s".format(s.clock.positionMs / 1000.0, t.durationMs / 1000.0, snap.frameAgeMs?.let { "${it}ms" } ?: "-"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayPause(s.clock, vm)
                ReplayClock.SPEEDS.forEach { sp ->
                    if (sp == s.clock.speed) Button(onClick = {}) { Text("${sp}x") }
                    else OutlinedButton(onClick = { vm.setSpeed(sp) }, modifier = Modifier.testTag("speed_$sp")) { Text("${sp}x") }
                }
                OutlinedButton(onClick = vm::back) { Text("닫기") }
            }
            Text("위험 근거 (02 RiskFrame)", fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.weight(1f)) {
                items(snap.risk?.riskObjects.orEmpty()) { o ->
                    Text("${o.vehicleId}  ${o.riskLevel}  ${o.reasonCodes.joinToString()}  TTC ${o.ttcS?.let { "%.1fs".format(it) } ?: "null"}", fontSize = 14.sp)
                }
                item { Spacer(Modifier.height(8.dp)); Text("이벤트 주석 (03 manifest)", fontWeight = FontWeight.Bold) }
                items(t.manifest.eventAnnotation) { e ->
                    Text("%.1fs  %s".format(e.tMs / 1000.0, e.label), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
                item { Spacer(Modifier.height(8.dp)); Text("데이터 검증 경고 ${t.issues.size}건", fontWeight = FontWeight.Bold) }
                items(t.issues) { Text(it.toString(), fontSize = 12.sp, color = Color.Gray) }
            }
        }
    }
}

@Composable
private fun ErrorScreen(s: UiState, vm: PrismViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("시나리오 '${s.failedScenario}' 를 재생할 수 없습니다", fontSize = 22.sp, color = Color(0xFFE57373), modifier = Modifier.testTag("error_title"))
        Text("JSON 계약 검증 결과 (${s.issues.count { it.severity == Severity.ERROR }}개 오류)")
        LazyColumn(Modifier.weight(1f, fill = false)) { items(s.issues) { Text(it.toString(), fontSize = 14.sp) } }
        Button(onClick = vm::back) { Text("시나리오 목록") }
    }
}

/** 04 visualization-engine 레이더. 차선 폭: 03 HighwayEnv 시나리오 4.0m, 05 Mock 3.6m. */
@Composable
private fun RadarSlot(s: UiState, snap: Snapshot, detail: Boolean, modifier: Modifier) = EngineRadar(
    snapshot = snap,
    nowMs = s.clock.positionMs,
    detail = detail,
    laneWidthM = if (s.timeline?.manifest?.mock == true) 3.6f else 4.0f,
    modifier = modifier,
)
