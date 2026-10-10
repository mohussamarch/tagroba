package app.masroufy.ui.screens.investment.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.AveragesFeed
import app.masroufy.core.GrowthClass
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.ApproxBadge
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.FeedState

/**
 * «لو وضعتها في…» (لوحة `SavingsGrowth` جوه `SavingsCalculator`): نفس المبلغ الشهري لنفس المدة في الخمسة بمعدلاتهم ومصادرهم
 * (`CompareSavingsGrowth`) · «بقيمة المال اليوم» · النسبة اللي المستخدم يكتبها بتغلب المتوسط (لوحة) · «ارجع للمتوسط».
 */
@Composable
fun SavingsGrowthPanel(monthlyMinor: Halalas, months: Int, startMinor: Halalas, today: IsoDate) {
    val space = LocalSpace.current
    val deps = space.investment.calculators
    val currency = space.space.currency
    var feed by remember(deps) { mutableStateOf<FeedState<AveragesFeed>?>(null) }
    LaunchedEffect(deps) { feed = runCatching { deps.averages() }.getOrElse { FeedState.Unavailable(it.message.orEmpty()) } }
    var ratesText by rememberSaveable { mutableStateOf("") }
    var todayMoney by rememberSaveable { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<GrowthClass?>(null) }
    var input by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val loaded = feed
    if (loaded == null) {
        Skeleton(Modifier.fillMaxWidth().height(320.dp))
        return
    }
    val userRates = decodeRates(ratesText)
    val averages = (loaded as? FeedState.Ready)?.feed
    val outcome = remember(monthlyMinor, months, startMinor, today, averages, userRates, todayMoney) {
        runCatching { deps.growth.compare(monthlyMinor, months, startMinor, today, averages, space.space.countryCode, userRates, todayMoney) }.getOrNull()
    } ?: return
    val ui = growthUi(outcome, currency)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(t(TextKey.SAVGROW_TITLE), style = Type.of(18, FontWeight.Bold))
            BasicText(ui.paidLine, style = Type.caption().copy(color = Ink.muted))
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x0D193D33)).padding(horizontal = 14.dp)) {
            CalcSwitchRow(t(TextKey.SAVGROW_TODAY), ui.todaySub, ui.todayOn, { todayMoney = !todayMoney }, subWarn = ui.todaySubWarn)
        }
        FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            ui.rows.forEachIndexed { i, r ->
                if (i > 0) Divider()
                GrowthRow(r, currency) {
                    sheetFor = r.growthClass
                    input = rateInputText(r.rateBp)
                    inputError = null
                }
                if (r.canReset) {
                    TonalButton(t(TextKey.SAVGROW_RESET), { ratesText = encodeRates(userRates - r.growthClass) }, Modifier.padding(bottom = 12.dp), height = 40.dp, muted = true)
                }
            }
        }
        FootNote(t(TextKey.SAVGROW_FOOT))
    }
    val cls = sheetFor
    Sheet(cls != null, { sheetFor = null }, cls?.let(::rateSheetTitle).orEmpty()) {
        if (cls != null) {
            BasicText(rateSheetBody(cls), style = Type.of(13).copy(color = Ink.muted))
            TextInput(
                input, { input = it; inputError = null }, placeholder = "0.00", error = inputError, ltr = true, keyboard = KeyboardType.Decimal, height = 56.dp, textSize = 24,
                trailing = { BasicText(t(TextKey.CALCUI_PERCENT_SIGN), Modifier.padding(end = 16.dp), style = Type.of(16).copy(color = Ink.muted)) },
            )
            PrimaryButton(t(TextKey.SAVGROW_SAVE), {
                val bp = parseRateBp(input)
                if (bp == null) inputError = t(TextKey.CALC_RATE_RANGE)
                else {
                    ratesText = encodeRates(userRates + (cls to bp))
                    sheetFor = null
                }
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GrowthRow(r: GrowthRowUi, currency: app.masroufy.core.Currency, onEdit: () -> Unit) {
    val (ink, icon) = classLook(r.growthClass)
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(ink) { LucideIcon(icon, size = 18.dp, tint = ink) }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(r.name, style = Type.bodyBold())
                    if (r.stale) ApproxBadge()
                }
                BasicText(r.rateText, style = if (r.rateMissing) Type.captionBold().copy(color = warnInk) else Type.caption().copy(color = Ink.muted))
            }
            Column(horizontalAlignment = Alignment.End) {
                AmountText(r.amountMinor, currency, showCurrency = false)
                r.gainMinor?.let { g -> AmountText(g, currency, tone = if (g > 0) AmountTone.INCOME else AmountTone.EXPENSE, size = 12, weight = FontWeight.Normal, showCurrency = false) }
            }
        }
        if (r.barPercent > 0) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x0F193D33))) {
                Box(Modifier.fillMaxWidth(r.barPercent / 100f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(ink))
            }
        }
        if (r.source.isNotEmpty()) BasicText(r.source, style = Type.caption().copy(color = Ink.muted))
        r.note?.let { NoteBox(it) }
        if (r.editStrong) PrimaryButton(r.editLabel, onEdit, height = 40.dp)
        else TonalButton(r.editLabel, onEdit, enabled = r.editEnabled, height = 40.dp)
    }
}

/** لون كل نوع وأيقونته (النموذج: ذهب كهرماني · عقار أخضر · وديعة أزرق · أسهم أزرق غامق · كاش رمادي). */
private fun classLook(cls: GrowthClass): Pair<Color, Lucide> = when (cls) {
    GrowthClass.GOLD -> Color(0xFF956000) to Lucide.COINS
    GrowthClass.REAL_ESTATE -> Ink.primary to Lucide.HOUSE
    GrowthClass.DEPOSIT -> Ink.transfer to Lucide.PIGGY_BANK
    GrowthClass.LOCAL_STOCKS -> Color(0xFF3866A7) to Lucide.TRENDING_UP
    GrowthClass.CASH -> Ink.muted to Lucide.BANKNOTE
}

/** النِسَب اللي المستخدم كتبها كنص يتحفظ مع الشاشة («gold:1488,deposit:450»). */
internal fun encodeRates(rates: Map<GrowthClass, Int>): String = rates.entries.joinToString(",") { "${it.key.wire}:${it.value}" }

internal fun decodeRates(text: String): Map<GrowthClass, Int> = text.split(',').mapNotNull { part ->
    val (wire, value) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
    val cls = GrowthClass.entries.firstOrNull { it.wire == wire } ?: return@mapNotNull null
    value.toIntOrNull()?.let { cls to it }
}.toMap()
