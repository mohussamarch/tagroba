package app.masroufy.ui.screens.investment.holdings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.HoldingPurpose
import app.masroufy.core.Id
import app.masroufy.core.InvestHoldKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «الغرض» في «تفاصيل الأصل» (مواصفة r05 §4.2): الأغراض الأربعة، والمختار = نفس اللي ظاهر في «حاجاتي» (من الصورة — بيحسب المشغولات
 * «شخصي» لو مفيش غرض مكتوب) ⇒ الضغط بيكتبه بـ`SetHoldingPurpose`. لسه بيحمّل أو مش لاقي الأصل ⇒ مفيش صف.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HoldingPurposeRow(assetId: Id) {
    val inv = LocalSpace.current.investment
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var current by remember(assetId) { mutableStateOf<HoldingPurpose?>(null) }
    LaunchedEffect(inv, assetId) {
        current = runCatching { inv.holdingsSnapshot.load(inv.today()).items.firstOrNull { it.asset.id == assetId }?.purpose }.getOrNull()
    }
    val selected = current ?: return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(t(InvestHoldKey.HOLD_F_PURPOSE), style = Type.of(13, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in HoldingPurpose.entries) {
                val on = p == selected
                val label = t(purposeKey(p))
                Row(
                    Modifier.height(40.dp).clip(RoundedCornerShape(14.dp))
                        .then(if (on) Modifier.background(Color.White).insetBorder(1.5.dp, Ink.primary, 14.dp) else Modifier.background(CHIP_FILL))
                        .holdTap(label) {
                            if (on) return@holdTap
                            scope.launch {
                                runCatching { inv.holdings.purpose.set(assetId, p) }
                                    .onSuccess { current = p }
                                    .onFailure { e -> e.message?.let { toaster.show(it) } }
                            }
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(purposeColor(p)))
                    BasicText(label, style = Type.of(13, FontWeight.Bold))
                }
            }
        }
    }
}
