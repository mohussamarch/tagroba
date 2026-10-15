package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.IncomeSource
import app.masroufy.core.PayerAccount
import app.masroufy.core.TextKey
import app.masroufy.core.UiKey
import app.masroufy.core.sentenceDigits
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.EmployerView
import kotlinx.coroutines.launch

/** لوحة الجهة المفتوحة: [source] null = «أضف جهة عمل». */
data class EmployerEdit(val source: IncomeSource?)

/**
 * «المزيد › جهات الشغل» (قايمة المالك 2026-10-15، البند 7 — `ManageEmployers`): كل جهة بفترتها (من · إلى) وحساب أو أكتر بيتحوّل منه المرتب
 * (الاسم وآخر ٤ أرقام بس). إضافة · تعديل · ربط حساب من التحويلات الداخلة · شيله. بعد أي إضافة أو تغيير ⇒ «طبّق على اللي فات» (`EmployerRetroBox`).
 */
@Composable
fun EmployersScreen() {
    val more = LocalSpace.current.more
    val employers = more.employers
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var list by remember(more) { mutableStateOf<List<EmployerView>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<EmployerEdit?>(null) }
    var addingTo by remember { mutableStateOf<Id?>(null) }
    var retroFor by remember { mutableStateOf<Id?>(null) }
    var retroRound by remember { mutableIntStateOf(0) }
    fun changed(id: Id) {
        retroFor = id
        retroRound++
        tick++
    }
    LaunchedEffect(more, tick) { list = employers?.let { runCatching { it.list() }.getOrNull() }.orEmpty() }
    InnerScaffold(t(UiKey.EMPLOYER_TITLE)) {
        item(key = "intro") { BasicText(t(UiKey.EMPLOYER_INTRO), Modifier.padding(horizontal = 4.dp), style = Type.of(13).copy(color = Ink.muted)) }
        if (employers == null) {
            item(key = "na") { EmptyState(t(TextKey.NOT_AVAILABLE)) }
            return@InnerScaffold
        }
        val rows = list
        if (rows == null) {
            item(key = "sk") { Skeleton(Modifier.fillMaxWidth().height(220.dp)) }
            return@InnerScaffold
        }
        val retro = more.employerRetro
        val target = retroFor
        if (retro != null && target != null) item(key = "retro-$target-$retroRound") { EmployerRetroBox(retro, target, onChanged = { tick++ }) }
        if (rows.isEmpty()) item(key = "empty") { EmptyState(t(UiKey.EMPLOYER_EMPTY_TITLE), t(UiKey.EMPLOYER_EMPTY_BODY)) }
        for (e in rows) {
            item(key = "e-${e.source.id}") {
                EmployerCard(e, onEdit = { editing = EmployerEdit(e.source) }, onAdd = { addingTo = e.source.id }, onRemove = { key ->
                    scope.launch {
                        val ok = runCatching { employers.detach(e.source.id, key) }.isSuccess
                        toaster.show(t(if (ok) UiKey.EMPLOYER_ACCOUNT_REMOVED else UiKey.MORE_SAVE_FAILED), dark = true)
                        tick++
                    }
                })
            }
        }
        item(key = "add") { PrimaryButton(t(UiKey.EMPLOYER_ADD), onClick = { editing = EmployerEdit(null) }, height = 52.dp, modifier = Modifier.fillMaxWidth()) }
    }
    EmployerEditSheet(editing, onDismiss = { editing = null }) { saved ->
        editing = null
        toaster.show(t(UiKey.EMPLOYER_SAVED), dark = true)
        changed(saved.id)
    }
    SenderPickSheet(addingTo, onDismiss = { addingTo = null }) { id ->
        addingTo = null
        toaster.show(t(UiKey.EMPLOYER_ACCOUNT_ADDED), dark = true)
        changed(id)
    }
}

/** «ينتهي بـ 4821» جنب اسم الحساب، أو الاسم بس لو مفيش أرقام. */
fun payerAccountLine(a: PayerAccount): String = a.last4?.let { t(UiKey.EMPLOYER_ACCOUNT_LAST4, a.label, sentenceDigits(it)) } ?: a.label

@Composable
private fun EmployerCard(e: EmployerView, onEdit: () -> Unit, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    val s = e.source
    FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(s.name, style = Type.of(15, FontWeight.Bold))
            BasicText(t(if (s.endedAt == null) UiKey.EMPLOYER_CURRENT else UiKey.EMPLOYER_ENDED) + "، " + employerPeriod(s), style = Type.caption().copy(color = Ink.muted))
            BasicText(t(UiKey.EMPLOYER_ACCOUNTS), style = Type.of(13, FontWeight.Bold))
            if (e.accounts.isEmpty()) BasicText(t(UiKey.EMPLOYER_NO_ACCOUNTS), style = Type.of(13).copy(color = Ink.muted))
            for (a in e.accounts) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(payerAccountLine(a), Modifier.weight(1f), style = Type.of(13))
                    TonalButton(t(UiKey.EMPLOYER_ACCOUNT_REMOVE), { onRemove(a.key) }, height = 36.dp, muted = true)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TonalButton(t(UiKey.EMPLOYER_ACCOUNT_ADD), onAdd, Modifier.weight(1f), height = 40.dp)
                TonalButton(t(UiKey.EMPLOYER_EDIT), onEdit, height = 40.dp, muted = true)
            }
        }
    }
}
