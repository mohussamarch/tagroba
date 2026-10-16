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
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.UiKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** «حساب ينتهي بـ…» أو الاسم — من القرار المحفوظ. */
private fun accountOf(p: TransferParty): String = partyAccount(TransferPartyRef(p.key, p.label, p.last4))

/**
 * «حسابات متجاهَلة» في «إعداد القراءة» (طلب المالك 2026-10-16 — «ليه لما اقول ليس هذا بتختفي؟»): كل حساب اتقال عليه «تجاهل الحساب ده»
 * و«رجّعه» (`TransferPartyAnswers.restore`) ⇒ بيتسأل عنه تاني. من غير ردود (المعاينة) ⇒ مفيش كارت.
 */
@Composable
fun IgnoredAccountsCard(modifier: Modifier = Modifier) {
    val answers = LocalSpace.current.operations.partyAnswers ?: return
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    var list by remember { mutableStateOf<List<TransferParty>?>(null) }
    LaunchedEffect(answers, version) { list = attempt { answers.ignored() } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.IGNORED_TITLE), style = Type.section())
        BasicText(t(UiKey.IGNORED_SUB), style = Type.of(12).copy(color = Ink.muted))
        FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            val rows = list.orEmpty()
            if (rows.isEmpty()) BasicText(t(UiKey.IGNORED_EMPTY), style = Type.of(13).copy(color = Ink.muted))
            for (p in rows) RuleRow(p.label, accountOf(p), t(UiKey.IGNORED_RESTORE)) {
                scope.launch {
                    toaster.show(failureOf { answers.restore(p.key) } ?: t(UiKey.IGNORED_RESTORED))
                    version++
                }
            }
        }
    }
}

/** قاعدة «بيحوّلك مستحقات دايمًا» في صفحة الشخص — «وقّف القاعدة» (`TransferPartyAnswers.stopDues`). مفيش قاعدة ⇒ مفيش كارت. */
@Composable
fun DuesRulesCard(personId: String, modifier: Modifier = Modifier) {
    val answers = LocalSpace.current.operations.partyAnswers ?: return
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    var list by remember { mutableStateOf<List<TransferParty>>(emptyList()) }
    LaunchedEffect(answers, personId, version) { list = attempt { answers.duesOf(personId) }.orEmpty() }
    if (list.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.DUES_RULE_TITLE), style = Type.section())
        FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            for (p in list) RuleRow(t(UiKey.DUES_RULE_ROW, accountOf(p)), null, t(UiKey.DUES_RULE_STOP)) {
                scope.launch {
                    toaster.show(failureOf { answers.stopDues(p.key) } ?: t(UiKey.DUES_RULE_STOPPED))
                    version++
                }
            }
        }
    }
}

@Composable
private fun RuleRow(title: String, sub: String?, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = Type.of(14, FontWeight.Bold))
            sub?.takeIf { it != title }?.let { BasicText(it, style = Type.of(12).copy(color = Ink.muted)) }
        }
        TonalButton(action, onClick, height = 40.dp)
    }
}
