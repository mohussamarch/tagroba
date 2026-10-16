package app.masroufy.ui.screens.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.PersonSuggestion
import app.masroufy.core.SuggestWhy
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.logicalArabic
import app.masroufy.core.transferPartyOf
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.operations.MeaningTarget
import app.masroufy.ui.screens.operations.PersonLinked
import app.masroufy.ui.screens.operations.PersonMeaningSheet
import app.masroufy.ui.screens.operations.UndoStrip
import app.masroufy.ui.screens.operations.dayLabel
import app.masroufy.ui.screens.operations.undoWindowFor
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.ManagePersonLinks
import kotlinx.coroutines.launch

/** اسم العملية في السطر: التاجر · الطرف زي ما جه · أول الوصف. */
internal fun opTitle(t: Transaction): String =
    (t.rawMerchantName ?: transferPartyOf(t)?.label ?: t.note ?: t.rawDescription)?.let { logicalArabic(it).trim().take(48) }.orEmpty()

internal fun whyLabel(w: SuggestWhy): String = t(
    when (w) {
        SuggestWhy.ACCOUNT -> UiKey.PLINK_WHY_ACCOUNT
        SuggestWhy.NAME -> UiKey.PLINK_WHY_NAME
        SuggestWhy.AMOUNT -> UiKey.PLINK_WHY_AMOUNT
    },
)

internal fun targetOf(t: Transaction, personId: Id?): MeaningTarget =
    MeaningTarget(t.id, t.observedDirection, opTitle(t), t.amountMinor, t.currency, personId, purchase = app.masroufy.core.isPurchaseShaped(t))

/**
 * «اربط عمليات موجودة» في صفحة الشخص (المالك 2026-10-16): اقتراحات (نفس الحساب · نفس الاسم · نفس مبلغ دين مفتوح) بضغطة ⇒ «معناها إيه؟» ⇒ ربط
 * بـ«تراجع» · «دوّر على أي عملية» · «أضف عملية مع الشخص ده» (`PersonAddOperation.kt`).
 */
@Composable
internal fun PersonLinkOpsCard(personId: Id, personName: String, links: ManagePersonLinks, today: IsoDate, modifier: Modifier = Modifier) {
    val space = LocalSpace.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val version = PeopleChanges.version
    var suggestions by remember(personId) { mutableStateOf<List<PersonSuggestion>?>(null) }
    // السرعة: الاقتراحات بتقرا كل العمليات ⇒ لما المالك يطلبها بس، وبرّه الخيط الرئيسي
    var wanted by remember(personId) { mutableStateOf(false) }
    LaunchedEffect(personId, version, wanted) {
        if (wanted) suggestions = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { links.page.suggestions(personId) } }.getOrNull()
    }
    var target by remember { mutableStateOf<MeaningTarget?>(null) }
    var undo by remember { mutableStateOf<PersonLinked?>(null) }
    val window = undoWindowFor(undo) { undo = null }
    var searching by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf<PersonLinked?>(null) }
    val addWindow = undoWindowFor(added) { added = null }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.PLINK_SUG_TITLE), style = Type.section())
        added?.let { a ->
            UndoStrip(a.text, addWindow) {
                added = null
                scope.launch {
                    toaster.show(runCatching { links.accounts.undoAdd(a.transactionId) }.exceptionOrNull()?.message ?: t(UiKey.PLINK_ADD_UNDONE))
                    PeopleChanges.bump()
                }
            }
        }
        undo?.let { u ->
            UndoStrip(u.text, window) {
                undo = null
                scope.launch {
                    toaster.show(runCatching { links.unlink(u.transactionId) }.exceptionOrNull()?.message ?: t(UiKey.PLINK_UNDONE))
                    PeopleChanges.bump()
                }
            }
        }
        FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            BasicText(t(UiKey.PLINK_SUG_BODY), Modifier.padding(top = 10.dp), style = Type.caption().copy(color = Ink.muted))
            val list = suggestions.orEmpty()
            if (!wanted) SecondaryButton(t(UiKey.PLINK_SUG_SHOW), onClick = { wanted = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            if (suggestions != null && list.isEmpty()) BasicText(t(UiKey.PLINK_SUG_EMPTY), Modifier.padding(vertical = 12.dp), style = Type.body().copy(color = Ink.muted))
            list.forEachIndexed { i, s -> OpRow(s.transaction, whyLabel(s.why), today, i == list.lastIndex) { target = targetOf(s.transaction, personId) } }
        }
        SecondaryButton(t(UiKey.PLINK_SUG_SEARCH), onClick = { searching = true }, modifier = Modifier.fillMaxWidth())
        PrimaryButton(t(UiKey.PLINK_ADD_ACTION), onClick = { adding = true }, modifier = Modifier.fillMaxWidth())
    }
    SearchSheet(searching, links, today, onDismiss = { searching = false }) { tx -> searching = false; target = targetOf(tx, personId) }
    PersonMeaningSheet(target, links, space.people.people, onDismiss = { target = null }) { linked ->
        target = null
        undo = linked
        PeopleChanges.bump()
    }
    PersonAddOperationSheet(adding, personId, personName, links, today, onDismiss = { adding = false }) { linked ->
        adding = false
        added = linked
        PeopleChanges.bump()
    }
}

@Composable
internal fun OpRow(t0: Transaction, chip: String?, today: IsoDate, last: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    val title = opTitle(t0)
    Rowed(last, Modifier.pressScale(press).tap(press, label = title, onClick = onClick)) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Type.bodyBold())
            BasicText(joinLine(dayLabel(t0.occurredAt.take(10), today), chip), style = Type.caption().copy(color = Ink.muted))
        }
        AmountText(t0.amountMinor, t0.currency, tone = if (t0.observedDirection == app.masroufy.core.Direction.IN) AmountTone.INCOME else AmountTone.EXPENSE, showCurrency = false)
    }
}

/** «دوّر على أي عملية»: بالاسم أو المبلغ في العمليات اللي مش مربوطة بحد (`LoadPersonPage.search`). */
@Composable
private fun SearchSheet(open: Boolean, links: ManagePersonLinks, today: IsoDate, onDismiss: () -> Unit, onPick: (Transaction) -> Unit) {
    var query by remember(open) { mutableStateOf("") }
    var hits by remember(open) { mutableStateOf<List<Transaction>?>(null) }
    LaunchedEffect(open, query) {
        if (!open || query.isBlank()) { hits = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(250)
        hits = runCatching { links.page.search(query) }.getOrNull()
    }
    Sheet(open, onDismiss, title = t(UiKey.PLINK_SUG_SEARCH), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 10.dp) {
        SheetHeading(t(UiKey.PLINK_SUG_SEARCH))
        TextInput(query, { query = it }, placeholder = t(UiKey.PLINK_SEARCH_HINT))
        val list = hits
        if (list != null && list.isEmpty()) BasicText(t(UiKey.PLINK_SEARCH_EMPTY), style = Type.body().copy(color = Ink.muted))
        list?.forEachIndexed { i, tx -> OpRow(tx, null, today, i == list.lastIndex) { onPick(tx) } }
    }
}
