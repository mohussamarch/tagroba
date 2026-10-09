package app.masroufy.ui.screens.investment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.RealEstateValuation
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.parseIsoDate
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.ApproxBadge
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SegmentStyle
import app.masroufy.ui.components.SegmentedTabs
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.IconButton44
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.AssetProjectionView
import app.masroufy.usecase.DefaultRatesView
import app.masroufy.usecase.FeedState
import kotlinx.coroutines.launch

/**
 * «الصورة كاملة» (لوحة `AssetProjection`): القيمة (سعر المتر × المساحة أو القيمة كاملة) · الإيجار · سنة البيع · الزيادة المتوقعة ⇒
 * `ManageAssetGrowth.project` (النسبة المكتوبة بتتبعت من غير حفظ) و«احفظ» ⇒ `setProfile`. كل رقم في النتيجة من حالة الاستخدام.
 */
@Composable
fun AssetProjectionScreen(assetId: Id) {
    val space = LocalSpace.current
    val deps = space.investment
    val currency = space.space.currency
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val today = deps.today()
    val thisYear = parseIsoDate(today).year
    var defaults by remember(space) { mutableStateOf<DefaultRatesView?>(null) }
    var view by remember(space, assetId) { mutableStateOf<AssetProjectionView?>(null) }
    var draft by remember(space, assetId) { mutableStateOf<ProjectionDraft?>(null) }
    var year by remember(space, assetId) { mutableStateOf(defaultSellYear(today)) }
    var error by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }

    suspend fun project(typed: Int?) {
        val d = defaults ?: deps.defaultRates.load((deps.feeds.averages() as? FeedState.Ready)?.feed, space.space.countryCode, today).also { defaults = it }
        try {
            val v = deps.growth.project(assetId, year, space.space.countryCode, d, typed)
            view = v
            if (draft == null) draft = draftOf(v.asset, currency, v.projection.currentValueMinor)
            failed = null
        } catch (e: IllegalArgumentException) {
            if (view == null) failed = e.message else error = e.message
        }
    }
    fun typedRate(): Int? = draft?.let { d -> (parseDraft(d, view?.asset ?: return null, currency) as? DraftParse.Ok)?.typedRateBp }
    LaunchedEffect(space, assetId, year) { project(typedRate()) }

    val v = view
    val d = draft
    InnerScaffold(t(TextKey.ASSET_DETAIL_FULL_TITLE)) {
        if (v == null || d == null) {
            item(key = "loading") {
                val f = failed
                if (f != null) AlertBanner(t(TextKey.SHELL_LOAD_FAILED), f) else Skeleton(Modifier.fillMaxWidth().height(220.dp))
            }
            return@InnerScaffold
        }
        val ui = projectionUi(v, currency)
        fun edit(next: ProjectionDraft) {
            draft = next
            error = (parseDraft(next, v.asset, currency) as? DraftParse.Invalid)?.message
        }
        item(key = "sub") { BasicText(ui.subtitle, style = Type.caption().copy(color = Ink.muted)) }
        if (ui.realEstate) item(key = "value") {
            ValueCard(ui, d, ::edit) {
                val parsed = parseDraft(d, v.asset, currency)
                if (parsed !is DraftParse.Ok) { error = (parsed as DraftParse.Invalid).message; return@ValueCard }
                scope.launch {
                    try {
                        deps.growth.setProfile(assetId, parsed.input)
                        toaster.show(t(TextKey.ASSET_PROJ_APPLIED))
                        project(parsed.typedRateBp)
                    } catch (e: IllegalArgumentException) { error = e.message }
                }
            }
        }
        item(key = "rent") { RentCard(d, ::edit) }
        item(key = "year") { YearCard(ui, onUp = { if (year < thisYear + 100) year += 1 }, onDown = { if (year > thisYear + 1) year -= 1 }) }
        item(key = "rate") {
            RateCard(ui, d) { text ->
                edit(d.copy(rate = text))
                val parsed = parseDraft(d.copy(rate = text), v.asset, currency)
                if (parsed is DraftParse.Ok) scope.launch { project(parsed.typedRateBp) }
            }
        }
        item(key = "result") { ResultCard(ui) }
        item(key = "save") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { FieldError(it) }
                PrimaryButton(t(TextKey.ASSET_PROJ_SAVE), {
                    val parsed = parseDraft(d, v.asset, currency)
                    if (parsed !is DraftParse.Ok) { error = (parsed as DraftParse.Invalid).message; return@PrimaryButton }
                    scope.launch {
                        try {
                            deps.growth.setProfile(assetId, parsed.input)
                            // «القيمة كاملة» = سعر الأصل (العقار وحدة واحدة) — ⚠️ لو الكمية مش وحدة واحدة القسمة مالهاش حالة استخدام (HANDOVER)
                            parsed.wholeMinor?.let { deps.assets.setPrice(assetId, it) }
                            draft = d.copy(rate = "")
                            project(null)
                            toaster.show(t(TextKey.ASSET_PROJ_SAVED, v.asset.name))
                        } catch (e: IllegalArgumentException) { error = e.message }
                    }
                }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ValueCard(ui: ProjectionUi, d: ProjectionDraft, onEdit: (ProjectionDraft) -> Unit, onApply: () -> Unit) {
    val currency = ui.currency
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(t(TextKey.ASSET_PROJ_VALUE), style = Type.of(15, FontWeight.Bold))
            SegmentedTabs(
                listOf(RealEstateValuation.AREA to t(TextKey.ASSET_PROJ_BY_AREA), RealEstateValuation.WHOLE to t(TextKey.ASSET_PROJ_WHOLE)),
                d.method ?: RealEstateValuation.AREA, { onEdit(d.copy(method = it)) }, Modifier.fillMaxWidth(), style = SegmentStyle.QUIET, height = 44.dp,
            )
            if (d.method != RealEstateValuation.WHOLE) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(t(TextKey.ASSET_PROJ_AREA), d.area, t(TextKey.ASSET_PROJ_SQM_UNIT), Modifier.weight(1f)) { onEdit(d.copy(area = it)) }
                    NumberField(t(TextKey.ASSET_PROJ_SQM), d.sqm, currencySymbol(currency), Modifier.weight(1f)) { onEdit(d.copy(sqm = it)) }
                }
            } else {
                NumberField(t(TextKey.ASSET_PROJ_WHOLE), d.whole, currencySymbol(currency), Modifier.fillMaxWidth()) { onEdit(d.copy(whole = it)) }
            }
            if (ui.valueMinor != null) AmountText(ui.valueMinor, currency, size = 16, color = Ink.primary)
            else BasicText(ui.gaps.firstOrNull() ?: t(TextKey.ASSET_DETAIL_NA_RE), style = Type.captionBold().copy(color = Ink.focus))
            if (d.method != RealEstateValuation.WHOLE) TonalButton(t(TextKey.ASSET_PROJ_APPLY), onApply, Modifier.fillMaxWidth(), height = 44.dp)
        }
    }
}

@Composable
private fun RentCard(d: ProjectionDraft, onEdit: (ProjectionDraft) -> Unit) {
    val currency = LocalSpace.current.space.currency
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                BasicText(t(TextKey.ASSET_PROJ_RENT), style = Type.of(15, FontWeight.Bold))
                BasicText(t(TextKey.ASSET_PROJ_RENT_SUB), style = Type.caption().copy(color = Ink.muted))
            }
            NumberField(t(TextKey.ASSET_PROJ_RENT_MONTHLY), d.rent, currencySymbol(currency), Modifier.fillMaxWidth()) { onEdit(d.copy(rent = it)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(t(TextKey.ASSET_PROJ_RENT_INC), d.increase, t(TextKey.INVEST_PERCENT, ""), Modifier.weight(1f)) { onEdit(d.copy(increase = it)) }
                NumberField(t(TextKey.ASSET_PROJ_VACANT), d.vacant, t(TextKey.ASSET_PROJ_MONTH_UNIT), Modifier.weight(1f)) { onEdit(d.copy(vacant = it)) }
            }
        }
    }
}

@Composable
private fun YearCard(ui: ProjectionUi, onUp: () -> Unit, onDown: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(t(TextKey.ASSET_PROJ_YEAR), style = Type.of(15, FontWeight.Bold))
                BasicText(ui.yearsLabel, style = Type.caption().copy(color = Ink.muted))
            }
            IconButton44(Lucide.PLUS, t(TextKey.ASSET_PROJ_YEAR_UP), onUp)
            BasicText(yearText(ui.sellYear), Modifier.widthIn(min = 64.dp), style = Type.of(20, FontWeight.Bold).copy(textAlign = TextAlign.Center))
            IconButton44(InvestmentIcons.MINUS, t(TextKey.ASSET_PROJ_YEAR_DOWN), onDown)
        }
    }
}

@Composable
private fun RateCard(ui: ProjectionUi, d: ProjectionDraft, onRate: (String) -> Unit) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(TextKey.ASSET_PROJ_RATE), Modifier.weight(1f), style = Type.of(15, FontWeight.Bold))
                val (ink, bg) = when (ui.chip) {
                    RateChip.TYPED -> Ink.transfer to Ink.transfer.copy(alpha = 0.10f)
                    RateChip.OLD -> Ink.focus to Ink.alertBg
                    RateChip.MISSING -> Ink.muted to QuietFill
                    else -> Ink.primary to Ink.selected
                }
                ToneChip(t(ui.chip.key), ink, bg)
            }
            BasicText(ui.rateValue, style = Type.of(20, FontWeight.Bold).copy(color = if (ui.rateKnown) Ink.text else Ink.muted))
            BasicText(ui.rateSource, style = Type.caption().copy(color = Ink.muted))
            NumberField(null, d.rate, t(TextKey.INVEST_PERCENT, ""), Modifier.fillMaxWidth(), onChange = onRate)
            BasicText(t(TextKey.ASSET_PROJ_RATE_INPUT), style = Type.caption().copy(color = Ink.muted))
        }
    }
}

@Composable
private fun ResultCard(ui: ProjectionUi) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.selected).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(ui.resultTitle, Modifier.weight(1f), style = Type.bodyBold().copy(color = Ink.primary))
            if (ui.approx) ApproxBadge()
        }
        AmountLine(ui.saleLabel, ui.saleMinor, ui.currency)
        AmountLine(ui.rentLabel, ui.rentMinor, ui.currency)
        AmountLine(t(TextKey.INVEST_RE_GAIN), ui.gainMinor, ui.currency, bold = true)
        for (g in ui.gaps) BasicText(g, style = Type.captionBold().copy(color = Ink.focus))
        BasicText(t(TextKey.ASSET_PROJ_METHOD), style = Type.caption().copy(color = Ink.soft))
    }
}

/** خانة رقم (من الشمال لليمين) بوحدتها على آخرها. */
@Composable
private fun NumberField(label: String?, value: String, unit: String, modifier: Modifier, onChange: (String) -> Unit) {
    TextInput(value, onChange, modifier, label = label, placeholder = "0", ltr = true, keyboard = KeyboardType.Decimal, trailing = { FieldUnit(unit) })
}
