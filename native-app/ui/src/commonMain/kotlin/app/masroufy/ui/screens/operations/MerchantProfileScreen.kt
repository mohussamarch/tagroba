package app.masroufy.ui.screens.operations

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.unit.dp
import app.masroufy.core.Category
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.ListRow
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.LoadTransactionsScreenRequest
import kotlinx.coroutines.launch

/**
 * صفحة التاجر (`MerchantProfile` — من رمز المحل جنب اسم العملية): الاسم ورمز تصنيفه (الشعار لسه مش في كوتلن ⇒ رمز التصنيف زي حالة «من غير نت»)
 * · كارتين الصرف عنده (⚠️ «غير متاح» — مفيش حالة استخدام لمجاميع التاجر) · تصنيفه في البلد دي (مؤكد/مقترح — `ManageRules.setMerchantCategory`)
 * · الأسماء البديلة (`addAlias`) · تغيير الاسم للعرض بس (`renameMerchant`) · عملياته في الشهر ده.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MerchantProfileScreen(merchantName: String) {
    val space = LocalSpace.current
    val deps = space.operations
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var view by remember(deps) { mutableStateOf<MerchantView?>(null) }
    var categories by remember(deps) { mutableStateOf<List<Category>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deps, reload) {
        val today = space.shell.today()
        categories = attempt { deps.categories.list() }.orEmpty()
        val merchants = attempt { deps.merchants.listMerchants().map { it.merchant } }.orEmpty()
        val data = attempt { deps.transactions.load(LoadTransactionsScreenRequest(today = today, payday = deps.payday())) }
        view = merchantView(merchantName, merchants, categories, data, attempt { deps.wallets() }.orEmpty(), today)
    }
    fun act(done: String, block: suspend () -> Unit) = scope.launch {
        val err = failureOf { block() }
        toaster.show(err ?: done)
        if (err == null) { sheet = null; reload++ }
    }
    InnerScaffold(t(UiKey.MERCHANT_PROFILE_TITLE)) {
        val v = view
        if (v == null) {
            item(key = "loading") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Skeleton(Modifier.fillMaxWidth().height(190.dp), radius = Radius.lensLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Skeleton(Modifier.weight(1f).height(84.dp))
                        Skeleton(Modifier.weight(1f).height(84.dp))
                    }
                    Skeleton(Modifier.fillMaxWidth().height(120.dp))
                }
            }
            return@InnerScaffold
        }
        val saved = v.merchantId != null
        item(key = "head") {
            FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.lensLarge), contentPadding = PaddingValues(18.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val color = categoryColor(v.category, Ink.muted)
                    IconTile(color, size = 64.dp, radius = Radius.control) { LucideIcon(categoryIcon(v.category?.iconKey), size = 28.dp, tint = color) }
                    BasicText(v.name, Modifier.padding(top = 4.dp), style = Type.of(20, FontWeight.Bold))
                    BasicText(t(UiKey.MERCHANT_PROFILE_IN_PERIOD, operationsCount(v.txns.size), v.periodLabel), style = Type.of(13).copy(color = Ink.muted))
                    TonalButton(t(UiKey.MERCHANT_PROFILE_RENAME), { sheet = "rename" }, enabled = saved, height = 44.dp)
                }
            }
        }
        item(key = "stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(t(UiKey.MERCHANT_PROFILE_SPENT_PERIOD, v.periodLabel), Modifier.weight(1f))
                StatCard(t(UiKey.MERCHANT_PROFILE_SPENT_ALL), Modifier.weight(1f))
            }
        }
        item(key = "category") {
            FloatingCard(Modifier.fillMaxWidth()) {
                BasicText(t(UiKey.MERCHANT_PROFILE_CATEGORY_IN, space.space.name), style = Type.caption().copy(color = Ink.muted))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(v.category?.name ?: t(UiKey.OPERATIONS_ROW_UNCLASSIFIED), style = Type.of(16, FontWeight.Bold))
                        if (v.category != null) {
                            if (v.confirmed) StatusChip(t(UiKey.OPERATION_DETAIL_CONFIRMED_CHIP), ChipInk.green, ChipInk.greenBg)
                            else StatusChip(t(UiKey.OPERATION_DETAIL_SUGGESTED), ChipInk.amber, ChipInk.amberBg)
                        }
                    }
                    val cat = v.category
                    val id = v.merchantId
                    if (!v.confirmed && cat != null && id != null) SmallAction(t(UiKey.OPERATION_DETAIL_CONFIRM), strong = true) {
                        act(t(UiKey.OPERATION_DETAIL_REMEMBERED, v.name, cat.name)) { deps.merchants.setMerchantCategory(id, cat.id) }
                    }
                    SmallAction(t(UiKey.OPERATION_DETAIL_CHANGE), enabled = saved) { sheet = "category" }
                }
                BasicText(merchantCategoryHint(v, space.space.name), style = Type.of(12).copy(color = Ink.muted))
            }
        }
        item(key = "aliases") { AliasesCard(v, saved) { raw -> v.merchantId?.let { id -> act(t(UiKey.MERCHANT_PROFILE_ALIAS_ADDED)) { deps.merchants.addAlias(id, raw) } } } }
        item(key = "list-title") { BasicText(t(UiKey.MERCHANT_PROFILE_LIST), Modifier.padding(top = 4.dp), style = Type.of(15, FontWeight.Bold)) }
        item(key = "list") {
            FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                if (v.txns.isEmpty()) BasicText(t(UiKey.MERCHANT_PROFILE_NONE_PERIOD, v.periodLabel), Modifier.padding(vertical = 14.dp), style = Type.of(13).copy(color = Ink.muted))
                v.txns.forEachIndexed { i, x ->
                    if (i > 0) Divider()
                    ListRow(x.date, subtitle = x.wallet, trailing = { AmountText(x.amountMinor, x.currency, tone = x.tone) }, onClick = { nav.push(OperationDetailRoute(x.id)) }, chevron = true)
                }
            }
        }
    }
    CategoryPicker(
        visible = sheet == "category",
        title = t(UiKey.CATEGORY_PICKER_TITLE, view?.name.orEmpty()),
        choices = pickerChoices(categories, true, view?.category?.id),
        selectedId = view?.category?.id,
        onDismiss = { sheet = null },
        body = t(UiKey.MERCHANT_PROFILE_CATEGORY_BODY),
    ) { cat ->
        val v = view ?: return@CategoryPicker
        val id = v.merchantId ?: return@CategoryPicker
        act(t(UiKey.OPERATION_DETAIL_REMEMBERED, v.name, cat.name)) { deps.merchants.setMerchantCategory(id, cat.id) }
    }
    RenameSheet(sheet == "rename", view?.name.orEmpty(), onDismiss = { sheet = null }) { name ->
        view?.merchantId?.let { id -> act(t(UiKey.MERCHANT_PROFILE_RENAMED)) { deps.merchants.renameMerchant(id, name) } }
    }
}

/** كارت الصرف عند التاجر — ⚠️ مفيش حالة استخدام لمجاميعه لسه ⇒ «غير متاح» (مش صفر — القاعدة 10). */
@Composable
private fun StatCard(label: String, modifier: Modifier) {
    FloatingCard(modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        BasicText(label, style = Type.caption().copy(color = Ink.muted))
        AmountText(null, app.masroufy.core.Currency.SAR, Modifier.fillMaxWidth(), tone = AmountTone.EXPENSE, size = 18)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AliasesCard(v: MerchantView, saved: Boolean, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val toaster = LocalToaster.current
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(UiKey.MERCHANT_PROFILE_ALIASES), style = Type.of(15, FontWeight.Bold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (a in v.aliases) BasicText(a, Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x0F193D33)).padding(horizontal = 10.dp, vertical = 6.dp), style = Type.of(13))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                TextInput(text, { if (it.length <= 120) text = it; error = null }, Modifier.weight(1f), placeholder = t(UiKey.MERCHANT_PROFILE_ALIAS_HINT), enabled = saved)
                PrimaryButton(t(UiKey.TAGS_SHEET_ADD), enabled = saved, onClick = {
                    when {
                        text.isBlank() -> error = t(TextKey.MERCHANT_ALIAS_LENGTH)
                        aliasExists(v, text) -> { toaster.show(t(UiKey.MERCHANT_PROFILE_ALIAS_EXISTS)); text = "" }
                        else -> { onAdd(text); text = "" }
                    }
                })
            }
            error?.let { FieldError(it) }
            BasicText(t(UiKey.MERCHANT_PROFILE_ALIAS_NOTE), style = Type.of(12).copy(color = Ink.muted))
        }
    }
}

/** «تغيير اسم التاجر»: الاسم للعرض بس — المطابقة مع الكشف بتفضل بالاسم الأصلي. */
@Composable
private fun RenameSheet(visible: Boolean, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var draft by remember(visible) { mutableStateOf(current) }
    var error by remember(visible) { mutableStateOf<String?>(null) }
    Sheet(visible, onDismiss, title = t(UiKey.MERCHANT_PROFILE_RENAME_TITLE), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        BasicText(t(UiKey.MERCHANT_PROFILE_RENAME_TITLE), style = Type.of(17, FontWeight.Bold))
        BasicText(t(UiKey.MERCHANT_PROFILE_RENAME_BODY), style = Type.of(13).copy(color = Ink.muted))
        TextInput(draft, { if (it.length <= 120) draft = it; error = null }, error = error)
        PrimaryButton(t(UiKey.LINK_PROJECT_EVENT_SAVE), modifier = Modifier.fillMaxWidth(), onClick = {
            if (draft.isBlank()) error = t(TextKey.MERCHANT_NAME_REQUIRED) else onSave(draft.trim())
        })
    }
}
