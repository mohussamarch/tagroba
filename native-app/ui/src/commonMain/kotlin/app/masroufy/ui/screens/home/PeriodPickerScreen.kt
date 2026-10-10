package app.masroufy.ui.screens.home

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.IsoDate
import app.masroufy.core.Period
import app.masroufy.core.TextKey
import app.masroufy.core.buildPeriod
import app.masroufy.core.parseIsoDate
import app.masroufy.core.periodForDate
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** يوم الراتب وأول بياناتك (أقدم رصيد بداية) — من `ManageProfile` و«معك الآن» (المحافظ). */
private data class PickerBase(val payday: Int, val dataStart: IsoDate?)

/**
 * «اختر الشهر»: البطاقة البطلة بالشهر المختار (سهم يمين = اللي فات · شمال = اللي بعده) ونطاقه بالتاريخ وشارة «الشهر الحالي/سابق» وشريط
 * اليوم كام من كام ⇒ «العودة إلى الشهر الحالي» ⇒ لوحة السنة (١٢ خانة). الاختيار بيتحفظ في [PeriodChoice] ورسالة صغيرة بتقوله.
 */
@Composable
fun PeriodPickerScreen() {
    val deps = LocalSpace.current
    val toaster = LocalToaster.current
    val today = deps.shell.today()
    var base by remember(deps) { mutableStateOf<PickerBase?>(null) }
    LaunchedEffect(deps) {
        val payday = runCatching { deps.home.profile.load().payday }.getOrDefault(DEFAULT_PAYDAY)
        val start = runCatching { deps.shell.withYouNow().wallets.minOfOrNull { it.wallet.openingAt } }.getOrNull()
        base = PickerBase(payday, start)
    }
    InnerScaffold(t(UiKey.PERIOD_PICKER_TITLE)) {
        val b = base
        if (b == null) {
            item(key = "loading") { Skeleton(Modifier.fillMaxWidth().height(180.dp), radius = 28.dp, strong = true) }
            return@InnerScaffold
        }
        val current = periodForDate(today, b.payday)
        val chosenKey = PeriodChoice.of(deps.space.id)
        val selected = chosenKey?.split("-")?.let { (y, m) -> buildPeriod(y.toInt(), m.toInt(), b.payday) }?.takeIf { it.start <= current.start } ?: current
        fun choose(p: Period, back: Boolean) {
            if (tileState(p, current, b.dataStart) != TileState.OPEN) return
            PeriodChoice.set(deps.space.id, if (p.key == current.key) null else p.key)
            val msg = if (back) t(UiKey.PERIOD_PICKER_BACK_TOAST, fiscalName(p)) else t(UiKey.PERIOD_PICKER_PICKED_TOAST, fiscalName(p))
            toaster.show(t(UiKey.PERIOD_PICKER_TOAST, msg, fiscalRange(p, withYear = false)))
        }
        item(key = "intro") { BasicText(t(UiKey.PERIOD_PICKER_INTRO, sentenceNumber(b.payday)), style = Type.of(13).copy(color = Ink.muted)) }
        item(key = "hero") {
            val prev = previousPeriod(selected, b.payday)
            val next = nextPeriod(selected, b.payday)
            SelectedCard(
                selected, current, today,
                canPrev = tileState(prev, current, b.dataStart) == TileState.OPEN, canNext = selected.key != current.key,
                onPrev = { choose(prev, false) }, onNext = { choose(next, false) },
            )
        }
        if (selected.key != current.key) item(key = "back") {
            TonalButton(t(UiKey.PERIOD_PICKER_BACK), onClick = { choose(current, true) }, modifier = Modifier.fillMaxWidth())
        }
        item(key = "board") { YearBoard(selected, current, b, onPick = { choose(it, false) }) }
    }
}

@Composable
private fun SelectedCard(sel: Period, current: Period, today: IsoDate, canPrev: Boolean, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    val isNow = sel.key == current.key
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassArrow(Lucide.CHEVRON_RIGHT, t(UiKey.PERIOD_PICKER_PREV), canPrev, onPrev)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(fiscalName(sel), style = Type.of(22, FontWeight.Bold).copy(color = Color.White, textAlign = TextAlign.Center))
                    BasicText(fiscalRange(sel, withYear = true), style = Type.of(13).copy(color = Ink.onHeroMuted, textAlign = TextAlign.Center))
                }
                GlassArrow(Lucide.CHEVRON_LEFT, t(UiKey.PERIOD_PICKER_NEXT), canNext, onNext)
            }
            BasicText(
                if (isNow) t(UiKey.PERIOD_PICKER_CURRENT) else t(UiKey.PERIOD_PICKER_PAST),
                Modifier.clip(RoundedCornerShape(12.dp)).background(if (isNow) Ink.mint else Color(0x29FFFFFF)).padding(horizontal = 12.dp, vertical = 3.dp),
                style = Type.of(12, FontWeight.Bold).copy(color = if (isNow) Ink.heroStart else Color.White),
            )
            if (isNow) {
                val pr = progressOf(current, today)
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ink.heroTrack)) {
                    Box(Modifier.fillMaxWidth(pr.day.coerceIn(0, pr.days).toFloat() / pr.days).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ink.heroProgress))
                }
            }
            BasicText(periodNote(sel, current, today), style = Type.of(13).copy(color = Ink.onHeroMuted, textAlign = TextAlign.Center))
        }
    }
}

@Composable
private fun GlassArrow(icon: Lucide, label: String, enabled: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.size(48.dp).alpha(if (enabled) 1f else 0.35f).pressScale(press, enabled).clip(RoundedCornerShape(16.dp))
            .background(Color(0x24FFFFFF)).tap(press, enabled, label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 22.dp, tint = Color.White, contentDescription = label, modifier = Modifier.mirrorInLtr()) }
}

@Composable
private fun YearBoard(sel: Period, current: Period, b: PickerBase, onPick: (Period) -> Unit) {
    var year by remember(sel.key) { mutableStateOf(parseIsoDate(sel.end).year) }
    val lastYear = parseIsoDate(current.end).year
    val firstYear = b.dataStart?.let { parseIsoDate(it).year }
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SoftArrow(Lucide.CHEVRON_RIGHT, t(UiKey.PERIOD_PICKER_YEAR_PREV), firstYear == null || year > firstYear) { year-- }
                BasicText(sentenceNumber(year), Modifier.weight(1f), style = Type.of(17, FontWeight.Bold).copy(textAlign = TextAlign.Center))
                SoftArrow(Lucide.CHEVRON_LEFT, t(UiKey.PERIOD_PICKER_YEAR_NEXT), year < lastYear) { year++ }
            }
            val tiles = yearTiles(year, b.payday, current, sel, b.dataStart)
            for (row in tiles.chunked(3)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (tile in row) Tile(tile, Modifier.weight(1f)) { onPick(tile.period) }
                }
            }
            val note = when {
                firstYear != null && year <= firstYear -> t(UiKey.PERIOD_PICKER_DATA_FROM, fiscalName(periodForDate(b.dataStart!!, b.payday)))
                year >= lastYear -> t(UiKey.PERIOD_PICKER_FUTURE_NOTE)
                else -> null
            }
            note?.let { BasicText(it, style = Type.caption().copy(color = Ink.muted)) }
        }
    }
}

@Composable
private fun SoftArrow(icon: Lucide, label: String, enabled: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.size(48.dp).alpha(if (enabled) 1f else 0.35f).pressScale(press, enabled).clip(RoundedCornerShape(16.dp))
            .background(Color(0x0D193D33)).tap(press, enabled, label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 22.dp, contentDescription = label, modifier = Modifier.mirrorInLtr()) }
}

@Composable
private fun Tile(tile: PeriodTile, modifier: Modifier, onClick: () -> Unit) {
    val press = rememberPress()
    val open = tile.state == TileState.OPEN
    val shape = RoundedCornerShape(16.dp)
    // المختار: الأخضر الأساسي · الشهر الحالي (مش مختار): حد أخضر 1.5 · الباقي: رمادي خفيف
    val surface = when {
        tile.selected -> Modifier.background(Glass.primary)
        tile.isNow -> Modifier.background(Color(0x0A193D33)).insetRing(shape, 1.5.dp, Ink.primary)
        else -> Modifier.background(Color(0x0A193D33))
    }
    Column(
        modifier.defaultMinSize(minHeight = 64.dp).alpha(if (open) 1f else 0.45f).pressScale(press, open).clip(shape).then(surface)
            .tap(press, open, label = tile.name, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        val ink = if (tile.selected) Color.White else Ink.text
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(tile.name, Modifier.weight(1f, fill = false), style = Type.of(14, FontWeight.Bold).copy(color = ink), maxLines = 1)
            if (tile.isNow) BasicText(
                t(UiKey.PERIOD_PICKER_NOW),
                Modifier.clip(RoundedCornerShape(10.dp)).background(if (tile.selected) Color(0x2EFFFFFF) else Ink.selected).padding(horizontal = 6.dp),
                style = Type.of(11, FontWeight.Bold).copy(color = if (tile.selected) Color.White else Ink.primary),
            )
        }
        BasicText(tile.range, style = Type.of(12).copy(color = if (tile.selected) Ink.onHeroMuted else Ink.muted), maxLines = 2)
    }
}
