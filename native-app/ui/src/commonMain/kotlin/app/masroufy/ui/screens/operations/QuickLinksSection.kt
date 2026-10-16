package app.masroufy.ui.screens.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.UiKey
import app.masroufy.core.dayMonth
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.money
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.QuickLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** نص المرشح في القسم. */
fun quickLinkText(link: QuickLink): String = when (link) {
    is QuickLink.Refund -> t(UiKey.QUICK_LINKS_REFUND, money(link.inflow.amountMinor, link.inflow.currency), dayMonth(link.inflow.occurredAt))
    is QuickLink.Salary -> t(UiKey.QUICK_LINKS_SALARY, link.employer.name)
}

/**
 * «ممكن تكون مربوطة بـ» فوق التفاصيل (2026-10-16): لحد 3 مرشحين من `QuickLinks` (منطق الربط الموجود بس) — بيتحسبوا **مرة لكل فتحة** وبرا الخيط
 * الرئيسي، وكل واحد بـ«اربط». مفيش مرشحين ⇒ القسم مش ظاهر.
 */
@Composable
fun QuickLinksSection(transactionId: Id, onChanged: () -> Unit) {
    val quick = LocalSpace.current.operations.quickLinks ?: return
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var list by remember(transactionId) { mutableStateOf<List<QuickLink>>(emptyList()) }
    var version by remember(transactionId) { mutableStateOf(0) }
    LaunchedEffect(transactionId, version) {
        list = withContext(Dispatchers.Default) { runCatching { quick.candidates(transactionId) }.getOrNull().orEmpty() }
    }
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.QUICK_LINKS_TITLE), style = Type.section())
        FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            for (l in list) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(quickLinkText(l), Modifier.weight(1f), style = Type.of(14, FontWeight.Bold))
                TonalButton(t(UiKey.QUICK_LINKS_LINK), {
                    scope.launch {
                        val r = runCatching { quick.link(transactionId, l) }
                        val refusal = r.getOrNull()
                        toaster.show(
                            when {
                                r.isFailure -> r.exceptionOrNull()?.message ?: t(UiKey.MORE_SAVE_FAILED)
                                refusal != null -> t(linkRefusalKey(refusal))
                                else -> t(UiKey.QUICK_LINKS_DONE)
                            },
                        )
                        version++
                        onChanged()
                    }
                }, height = 40.dp)
            }
            BasicText(t(UiKey.QUICK_LINKS_NOTE), style = Type.of(12).copy(color = Ink.muted))
        }
    }
}
