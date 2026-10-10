package app.masroufy.ui.screens.operations

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.Category
import app.masroufy.core.Id
import app.masroufy.core.Tag
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconButton44
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** حالة شاشة التفاصيل. [locked] = رجل تحويل لنفسك بين بلدين (ما بتتربطش بشخص ولا مشروع ولا حدث). */
sealed interface DetailState {
    data object Loading : DetailState

    data object Failed : DetailState

    data class Ready(val view: DetailView, val tags: List<Tag>, val categories: List<Category>, val locked: Boolean) : DetailState
}

/**
 * تفاصيل العملية (`OperationDetail` + `CategoryPicker`): الكارت الكبير (الأيقونة · الاسم ورمز صفحة التاجر · التاريخ والاتجاه · المبلغ · القفل) ·
 * «مَن هذا؟» للحوالة الداخلة من طرف جديد · الحقول (المحفظة · التصنيف مقترح/مؤكد · النوع) · الوسوم · «اربطها بـ» (شخص · مشروع أو حدث).
 * التاريخ والاتجاه ما بيتعدلوش (§13). كل تغيير عن طريق حالة الاستخدام، والشاشة بتحمّل من جديد بعده.
 */
@Composable
fun OperationDetailScreen(transactionId: Id, openPerson: Boolean = false) {
    val space = LocalSpace.current
    val deps = space.operations
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var state by remember(deps) { mutableStateOf<DetailState>(DetailState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    // كارت «مَن هذا؟» بيفضل بعد القرار (بحالة «تم» و«تراجع») حتى لو التحميل الجديد شال السؤال
    var partyShown by remember { mutableStateOf<app.masroufy.core.TransferPartyRef?>(null) }
    LaunchedEffect(deps, reload) {
        state = attempt {
            val detail = deps.edit.load(transactionId)
            val categories = deps.categories.list()
            val merchants = attempt { deps.merchants.listMerchants().map { it.merchant } }.orEmpty()
            val decided = attempt { deps.transfers.zone().rows.filter { it.decision != null }.map { it.party.key }.toSet() }.orEmpty()
            val locked = attempt { deps.spaceTransfers.legsIn(space.space.id).isLeg(transactionId) } ?: false
            val view = detailView(detail.transaction, categories, attempt { deps.wallets() }.orEmpty(), merchants, decided, space.shell.today())
            DetailState.Ready(view, detail.tags, categories, locked)
        } ?: DetailState.Failed
        (state as? DetailState.Ready)?.view?.askParty?.let { partyShown = it }
    }
    val ready = state as? DetailState.Ready
    InnerScaffold(t(TextKey.OPERATION_DETAIL_TITLE)) {
        when (val s = state) {
            DetailState.Loading -> item(key = "loading") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Skeleton(Modifier.fillMaxWidth().height(190.dp), radius = Radius.lensLarge)
                    Skeleton(Modifier.fillMaxWidth().height(220.dp))
                }
            }
            DetailState.Failed -> item(key = "error") {
                ErrorBanner(t(TextKey.OPERATION_DETAIL_ERROR_TITLE), t(TextKey.OPERATIONS_ERROR_BODY), t(TextKey.SHELL_RETRY), { reload++ })
            }
            is DetailState.Ready -> {
                val v = s.view
                item(key = "hero") { DetailHero(v) }
                if (v.unrecorded && v.category?.status == CategoryStatus.NONE) item(key = "cash") { CashDifferenceBox { picking = true } }
                val party = v.askParty ?: partyShown
                if (party != null) item(key = "party") { TransferPartyCard(party, v.id, onChanged = { reload++ }) }
                item(key = "fields") {
                    FieldsCard(v, s.tags, onPick = { picking = true }, onConfirm = { id ->
                        scope.launch {
                            val err = failureOf { deps.edit.setCategory(v.id, id) }
                            toaster.show(err ?: t(TextKey.OPERATION_DETAIL_CONFIRMED))
                            reload++
                        }
                    }, onTagsChanged = { reload++ })
                }
                if (v.flow != LinkFlow.NONE) item(key = "links") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicText(t(TextKey.OPERATION_DETAIL_LINK_TITLE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
                        LinkPersonCard(v, s.locked, openInitially = openPerson)
                        LinkProjectEventCard(v, s.locked)
                    }
                }
            }
        }
    }
    CategoryPicker(
        visible = picking && ready != null,
        title = ready?.view?.let { if (it.unrecorded) t(TextKey.CATEGORY_PICKER_CASH_TITLE) else t(TextKey.CATEGORY_PICKER_TITLE, it.title) }.orEmpty(),
        choices = ready?.let { pickerChoices(it.categories, it.view) }.orEmpty(),
        selectedId = ready?.view?.category?.categoryId,
        onDismiss = { picking = false },
        onPick = { cat ->
            val v = ready?.view ?: return@CategoryPicker
            picking = false
            scope.launch {
                val err = failureOf { deps.edit.setCategory(v.id, cat.id) }
                if (err != null) {
                    toaster.show(err)
                    return@launch
                }
                // اختيار بإيدك بيتثبّت على التاجر المحفوظ (§75-16 — من غير سؤال «نفتكره؟»)
                val merchant = v.merchant
                val remembered = merchant != null && failureOf { deps.merchants.setMerchantCategory(merchant.id, cat.id) } == null
                toaster.show(categoryToast(remembered, v.title, cat.name))
                reload++
            }
        },
    )
}

/** الكارت الكبير في النص (زاوية 26 · حشوة 18). */
@Composable
private fun DetailHero(v: DetailView) {
    val nav = LocalNavigator.current
    FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.lensLarge), contentPadding = PaddingValues(18.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RowTile(OpRow(v.id, v.title, v.sub, v.icon, v.color, v.amountMinor, v.currency, v.tone, v.unrecorded), size = 56)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(v.title, style = Type.of(18, FontWeight.Bold).copy(textAlign = TextAlign.Center))
                val name = v.merchantName
                if (name != null) IconButton44(OperationsIcons.STORE, t(TextKey.OPERATION_DETAIL_MERCHANT), onClick = { nav.push(MerchantProfileRoute(name)) })
            }
            BasicText(v.sub, style = Type.of(13).copy(color = Ink.muted))
            AmountText(v.amountMinor, v.currency, tone = v.tone, size = 30)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(Lucide.LOCK, size = 14.dp, tint = Ink.muted)
                BasicText(t(if (v.unrecorded) TextKey.OPERATION_DETAIL_LOCK_CASH else TextKey.OPERATION_DETAIL_LOCK), style = Type.caption().copy(color = Ink.muted))
            }
        }
    }
}

/** «هذه ليست عملية مسجّلة» — فرق عدّ الكاش بنقطة حمرا لحد ما تحدد فيمَ صُرف (§73). */
@Composable
private fun CashDifferenceBox(onPick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(Color(0x14BE3D48)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(t(TextKey.OPERATION_DETAIL_CASH_TITLE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.expense))
        BasicText(t(TextKey.OPERATION_DETAIL_CASH_BODY), style = Type.of(13))
        PrimaryButton(t(TextKey.CATEGORY_PICKER_CASH_TITLE), onClick = onPick, modifier = Modifier.fillMaxWidth())
    }
}

/** كارت الحقول: المحفظة · التصنيف (بأفعاله) · النوع — وتحتهم الوسوم. */
@Composable
private fun FieldsCard(v: DetailView, tags: List<Tag>, onPick: () -> Unit, onConfirm: (Id) -> Unit, onTagsChanged: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        FieldRow(v.fields.first())
        v.category?.let { c ->
            Divider()
            val field = when (c.status) {
                CategoryStatus.SUGGESTED -> DetailField(t(TextKey.OPERATION_DETAIL_CATEGORY), c.name.orEmpty(), FieldChip(t(TextKey.OPERATION_DETAIL_SUGGESTED), ChipInk.amber, ChipInk.amberBg), t(TextKey.OPERATION_DETAIL_SUGGESTED_HINT))
                CategoryStatus.CONFIRMED -> DetailField(t(TextKey.OPERATION_DETAIL_CATEGORY), c.name.orEmpty(), FieldChip(t(TextKey.OPERATION_DETAIL_CONFIRMED_CHIP), ChipInk.green, ChipInk.greenBg))
                CategoryStatus.NONE -> DetailField(t(TextKey.OPERATION_DETAIL_CATEGORY), t(TextKey.OPERATIONS_ROW_UNCLASSIFIED))
            }
            FieldRow(field) {
                when (c.status) {
                    CategoryStatus.SUGGESTED -> {
                        SmallAction(t(TextKey.OPERATION_DETAIL_CHANGE), onClick = onPick)
                        SmallAction(t(TextKey.OPERATION_DETAIL_CONFIRM), strong = true) { c.categoryId?.let(onConfirm) }
                    }
                    CategoryStatus.CONFIRMED -> SmallAction(t(TextKey.OPERATION_DETAIL_CHANGE), onClick = onPick)
                    CategoryStatus.NONE -> SmallAction(t(TextKey.OPERATION_DETAIL_CATEGORIZE), strong = true, onClick = onPick)
                }
            }
        }
        for (f in v.fields.drop(1)) {
            Divider()
            FieldRow(f)
        }
        Divider()
        Box(Modifier.padding(vertical = 6.dp)) { TagsRow(v.id, tags, onTagsChanged) }
    }
}

@Composable
private fun FieldRow(f: DetailField, actions: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(f.label, style = Type.caption().copy(color = Ink.muted))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(f.value, style = Type.of(15, FontWeight.Bold))
                f.chip?.let { StatusChip(it.text, it.ink, it.background) }
            }
            f.hint?.let { BasicText(it, style = Type.caption().copy(color = Ink.muted)) }
        }
        actions?.invoke()
    }
}
