package app.masroufy.ui.screens.operations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.masroufy.core.Category
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.UiKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.screens.imports.CategoryPickerSheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import kotlinx.coroutines.launch

/**
 * «إيه الصنف؟» بعد اختيار «شراء» (طلب المالك 2026-10-16 — «لما اختار … انها شراء المفروض يطلعلي بوب اب قايمة تسألني عن الصنف … وانا ممكن ادوس
 * تخطي»): أي مكان النوع بيتحط فيه «شراء» (لوحة النوع في التفاصيل · التحديد/القايمة السريعة · المراجعة) بيفتح لوحة اختيار التصنيف الموجودة
 * (مجموعة ← أساسي ← فرعي + «آخر اللي استخدمته»). «تخطّي» ⇒ ولا كتابة (العملية بتفضل من غير تصنيف).
 */
data class PurchaseAsk(val ids: List<Id>)

/** بعد ما النوع اتحفظ: «شراء» على عملية أو أكتر ⇒ السؤال؛ غير كده ⇒ null. */
fun purchaseAskAfter(kind: EconomicKind, ids: List<Id>): PurchaseAsk? =
    if (kind != EconomicKind.PURCHASE || ids.isEmpty()) null else PurchaseAsk(ids.distinct())

/** اللي بيتكتب بعد الاختيار: [categoryId] على كل [ids]. «تخطّي» ⇒ null (مفيش كتابة). */
data class PurchaseSave(val ids: List<Id>, val categoryId: Id)

fun PurchaseAsk.pick(category: Category): PurchaseSave = PurchaseSave(ids, category.id)

fun PurchaseAsk.skip(): PurchaseSave? = null

/** اللوحة نفسها: [ask] غير null ⇒ ظاهرة. [onClose] بعد الحفظ أو «تخطّي» أو القفل. */
@Composable
fun PurchaseCategorySheet(ask: PurchaseAsk?, onClose: (saved: Boolean) -> Unit) {
    val deps = LocalSpace.current.operations
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var cats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var recent by remember { mutableStateOf<List<Category>>(emptyList()) }
    LaunchedEffect(ask) {
        if (ask == null) return@LaunchedEffect
        cats = attempt { deps.categories.list() }.orEmpty()
        recent = attempt { deps.recentCategories?.forPurchase() }.orEmpty()
    }
    CategoryPickerSheet(
        visible = ask != null,
        title = t(UiKey.PURCHASE_ASK_TITLE),
        categories = cats,
        selectedId = null,
        onPick = { c ->
            val save = ask?.pick(c) ?: return@CategoryPickerSheet
            scope.launch {
                val bulk = deps.bulk
                val err = failureOf {
                    if (save.ids.size > 1 && bulk != null) bulk.setCategory(save.ids, save.categoryId)
                    else for (id in save.ids) deps.edit.setCategory(id, save.categoryId)
                }
                toaster.show(err ?: t(UiKey.PURCHASE_ASK_DONE, c.name))
                onClose(err == null)
            }
        },
        onDismiss = { ask?.skip(); onClose(false) },
        body = t(UiKey.PURCHASE_ASK_BODY),
        recent = recent,
        skipLabel = t(UiKey.PURCHASE_ASK_SKIP),
    )
}
