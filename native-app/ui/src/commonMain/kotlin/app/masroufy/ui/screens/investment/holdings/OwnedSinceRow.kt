package app.masroufy.ui.screens.investment.holdings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.IsoDate
import app.masroufy.core.R06Key
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.screens.people.DateField
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * §120-14 «امتلكتها إمتى؟» (كلمة المالك — ورث أو جايزة أو هدية مش شرا بس) — **اختياري**: «من زمان» مختار من الأول (= أي تاريخ بيع
 * بيتقبل — `openingLotDate`)، والضغط على التاريخ بيفتح الشهر. التاريخ بعد النهارده بترفضه حالة الاستخدام (`AddHolding`).
 */
@Composable
internal fun OwnedSinceRow(ownedSince: IsoDate?, today: IsoDate, onChange: (IsoDate?) -> Unit) {
    var picking by remember { mutableStateOf(ownedSince != null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(t(R06Key.C120_OWNED_SINCE), style = Type.of(13, FontWeight.Bold))
        BasicText(t(R06Key.C120_OWNED_SINCE_HINT), style = Type.of(12).copy(color = Ink.muted))
        SelectChip(t(R06Key.C120_OWNED_LONG_AGO), !picking && ownedSince == null, { picking = false; onChange(null) })
        DateField(ownedSince, today, onPick = { picking = true; onChange(it) }, noFuture = true)
    }
}
