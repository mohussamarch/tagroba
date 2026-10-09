package app.masroufy.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.periodForDate
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.ListRow
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** حالة اللوحة: بيحمّل ⇒ جاهزة (أو null = مفيش محفظة كاش ⇒ «غير متاح») ⇒ فشل. */
private sealed interface CashLoad {
    data object Loading : CashLoad

    data object Failed : CashLoad

    data class Ready(val view: CashView?) : CashLoad
}

/**
 * لوحة الكاش (`CashDetails` — جوه `Main`): من شريحة «يشمل X كاش». الطول ثابت (٤٢٠) وهي بتحمّل، والصفوف بتتمرر جواها مع اللوحة.
 */
@Composable
internal fun CashDetailsSheet(visible: Boolean, onDismiss: () -> Unit) {
    val deps = LocalSpace.current
    var load by remember(deps) { mutableStateOf<CashLoad>(CashLoad.Loading) }
    LaunchedEffect(deps, visible) {
        if (!visible) return@LaunchedEffect
        load = CashLoad.Loading
        load = runCatching {
            val today = deps.shell.today()
            val payday = runCatching { deps.home.profile.load().payday }.getOrDefault(DEFAULT_PAYDAY)
            CashLoad.Ready(cashViewOf(deps.home.cash.load(periodForDate(today, payday), today)))
        }.getOrElse { CashLoad.Failed }
    }
    Sheet(visible, onDismiss, t(TextKey.CASH_DETAILS_TITLE), minHeight = 420.dp, closeLabel = t(TextKey.SHELL_CLOSE)) {
        when (val s = load) {
            CashLoad.Loading -> {
                Skeleton(Modifier.fillMaxWidth().height(64.dp))
                Skeleton(Modifier.fillMaxWidth().height(44.dp), radius = 16.dp)
                Skeleton(Modifier.fillMaxWidth().height(120.dp))
            }
            CashLoad.Failed -> EmptyState(t(TextKey.SHELL_LOAD_FAILED))
            is CashLoad.Ready -> {
                val v = s.view
                if (v == null) EmptyState(t(TextKey.CASH_DETAILS_NO_WALLET_TITLE), t(TextKey.CASH_DETAILS_NO_WALLET_BODY))
                else CashBody(v)
            }
        }
    }
}

@Composable
private fun CashBody(v: CashView) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            BasicText(t(TextKey.CASH_DETAILS_TITLE), style = Type.section())
            BasicText(v.sinceLine, style = Type.caption().copy(color = Ink.muted))
        }
        AmountText(v.balanceMinor, v.currency, size = 30)
    }
    // صرفت كاش هذا الشهر — شريط أخضر فاتح زي النموذج
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.selected).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(t(TextKey.CASH_DETAILS_SPENT), style = Type.of(13))
        AmountText(v.spentInPeriodMinor, v.currency, size = 13)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Flow(t(TextKey.CASH_DETAILS_IN), v.inSinceOpeningMinor, v, AmountTone.INCOME, Modifier.weight(1f))
        Flow(t(TextKey.CASH_DETAILS_OUT), v.outSinceOpeningMinor, v, AmountTone.EXPENSE, Modifier.weight(1f))
    }
    BasicText(t(TextKey.CASH_DETAILS_OPS), style = Type.of(13, androidx.compose.ui.text.font.FontWeight.Bold).copy(color = Ink.muted))
    if (v.rows.isEmpty()) BasicText(t(TextKey.CASH_DETAILS_NO_OPS), style = Type.of(13).copy(color = Ink.muted))
    v.rows.forEachIndexed { i, r ->
        ListRow(
            title = r.title,
            subtitle = dayMonth(r.date),
            trailing = { AmountText(r.amountMinor, v.currency, tone = r.tone, showCurrency = false) },
            chevron = false,
        )
        if (i < v.rows.lastIndex) Divider()
    }
    BasicText(t(TextKey.CASH_DETAILS_NOTE), style = Type.caption().copy(color = Ink.muted))
}

/** مربع «دخل الكاش» / «خرج منه» من يوم رصيد البداية. */
@Composable
private fun Flow(label: String, minor: Long, v: CashView, tone: AmountTone, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Ink.surface).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(label, style = Type.caption().copy(color = Ink.muted))
        AmountText(minor, v.currency, tone = tone, size = 15)
    }
}
