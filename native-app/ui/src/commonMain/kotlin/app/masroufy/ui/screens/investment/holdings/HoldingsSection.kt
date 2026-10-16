package app.masroufy.ui.screens.investment.holdings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.GroupTile
import app.masroufy.core.HoldingGroup
import app.masroufy.core.HoldingPurpose
import app.masroufy.core.HoldingsViewModel
import app.masroufy.core.InvestHoldKey
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «حاجاتي» نفسها (النموذج `InvestHoldings`): العنوان والإجمالي · الملاحظة · كارت الشريط والفلتر · مربع لكل مجموعة + «ضيف حاجة».
 * عرض بس — الأرقام كلها من [HoldingsViewModel] (`holdingsView` في `core`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HoldingsBlock(
    view: HoldingsViewModel,
    currency: Currency,
    filter: HoldingPurpose?,
    onFilter: (HoldingPurpose?) -> Unit,
    onOpen: (HoldingGroup) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onIncludePersonal: (Boolean) -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            BasicText(t(InvestHoldKey.HOLD_TITLE), style = Type.of(18, FontWeight.Bold, 1.4))
            if (!view.empty) {
                val total = view.totalMinor
                BasicText(if (total == null) t(InvestHoldKey.HOLD_NA) else wholeMoney(total, currency), style = Type.of(15, FontWeight.Bold, 1.4))
            }
        }
        val note = when {
            view.empty -> InvestHoldKey.HOLD_NOTE_EMPTY
            view.goldPriceMissing -> InvestHoldKey.HOLD_NOTE_GOLD_NA
            view.includePersonal -> InvestHoldKey.HOLD_NOTE_WITH_PERSONAL
            else -> InvestHoldKey.HOLD_NOTE_NORMAL
        }
        BasicText(t(note), Modifier.offset(y = (-8).dp), style = Type.of(12).copy(color = Ink.muted))
        if (!view.empty && view.strip.isNotEmpty()) PurposeCard(view, filter, onFilter, onIncludePersonal)
        TilesGrid(view.tiles, onOpen, onAdd)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurposeCard(view: HoldingsViewModel, filter: HoldingPurpose?, onFilter: (HoldingPurpose?) -> Unit, onIncludePersonal: (Boolean) -> Unit) {
    FloatingCard(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        val aria = view.strip.joinToString("، ") { t(purposeKey(it.purpose)) + " " + it.percent + "%" }
        Row(Modifier.fillMaxWidth().height(10.dp).semantics { contentDescription = aria }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val last = view.strip.lastIndex
            view.strip.forEachIndexed { i, share ->
                val shape = when {
                    last == 0 -> RoundedCornerShape(5.dp)
                    i == 0 -> RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp)
                    i == last -> RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                // العرض نسبة من القيمة — هندسة رسم بس، مش حساب فلوس
                Box(
                    Modifier.weight(share.valueMinor.toFloat().coerceAtLeast(1f)).widthIn(min = 6.dp).fillMaxHeight()
                        .alpha(if (filter == null || filter == share.purpose) 1f else 0.25f).clip(shape).background(purposeColor(share.purpose)),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            Modifier.fillMaxWidth().semantics { contentDescription = t(InvestHoldKey.HOLD_FILTER_LABEL) },
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PurposeChip(t(InvestHoldKey.HOLD_ALL), null, null, filter == null) { onFilter(null) }
            for (share in view.strip) {
                PurposeChip(t(purposeKey(share.purpose)), share.percent, share.purpose, filter == share.purpose) {
                    onFilter(if (filter == share.purpose) null else share.purpose)
                }
            }
        }
        // رد المالك 2026-10-16 ٢أ: المستخدم بيختار، والافتراضي من غير الشخصي (بيتحفظ على الحساب)
        if (view.hasPersonal) {
            val question = t(InvestHoldKey.HOLD_INCLUDE_PERSONAL)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(question, Modifier.weight(1f), style = Type.of(13, FontWeight.SemiBold, 1.4).copy(color = Ink.soft))
                app.masroufy.ui.screens.budgets.Switch(view.includePersonal, question, { onIncludePersonal(!view.includePersonal) })
            }
        }
    }
}

@Composable
private fun PurposeChip(label: String, percent: Int?, purpose: HoldingPurpose?, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.height(36.dp).clip(shape)
            .then(if (on) Modifier.background(androidx.compose.ui.graphics.Color.White).insetBorder(1.5.dp, Ink.primary, 12.dp) else Modifier.background(SOFT_FILL))
            .holdTap(label, onClick).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (purpose != null) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(purposeColor(purpose)))
        BasicText(label, style = Type.of(12, FontWeight.Bold))
        if (percent != null) BasicText("$percent%", style = Type.of(12, FontWeight.Bold).copy(color = Ink.muted))
    }
}

@Composable
private fun TilesGrid(tiles: List<GroupTile>, onOpen: (HoldingGroup) -> Unit, onAdd: () -> Unit) {
    val cells: List<GroupTile?> = tiles + listOf(null) // null = «ضيف حاجة» في الآخر
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in cells.chunked(3)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (cell in row) {
                    val m = Modifier.weight(1f).fillMaxHeight().heightIn(min = 132.dp)
                    if (cell == null) AddTile(t(InvestHoldKey.HOLD_ADD), m, onAdd) else GroupTileView(cell, m) { onOpen(cell.group) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GroupTileView(tile: GroupTile, modifier: Modifier, onClick: () -> Unit) {
    val name = t(groupKey(tile.group))
    val value = tile.valueMinor
    val valueText = if (value == null) t(InvestHoldKey.HOLD_NA) else wholeText(value)
    val sub = if (tile.count == 1) tile.names.first() else countText(tile.count) + " · " + tile.names.take(2).joinToString("، ")
    val aria = name + "، " + valueText + "، " + countText(tile.count)
    FloatingCard(modifier, shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(10.dp), onClick = onClick, clickLabel = aria) {
        val (bg, fg) = groupBox(tile.group)
        Box(Modifier.padding(bottom = 4.dp).size(36.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
            LucideIcon(groupIcon(tile.group), size = 20.dp, tint = fg)
        }
        BasicText(name, style = Type.of(13, FontWeight.Bold, 1.4))
        BasicText(valueText, style = Type.of(13, FontWeight.Bold).copy(color = if (value == null) Ink.muted else Ink.text))
        BasicText(sub, style = Type.of(11, lineHeight = 1.4).copy(color = Ink.muted), maxLines = 2)
        Spacer(Modifier.weight(1f).heightIn(min = 2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { for (p in tile.purposes) PurposeTag(p) }
    }
}

@Composable
internal fun PurposeTag(p: HoldingPurpose) {
    BasicText(
        t(purposeKey(p)),
        Modifier.clip(RoundedCornerShape(6.dp)).background(purposeColor(p).copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 1.dp),
        style = Type.of(10, FontWeight.Bold),
    )
}

@Composable
private fun AddTile(label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.dashedBorder(18.dp).clip(RoundedCornerShape(18.dp)).background(ADD_FILL).holdTap(label, onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LucideIcon(HoldIcons.plus, size = 22.dp, tint = Ink.primary)
        BasicText(label, style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
    }
}
