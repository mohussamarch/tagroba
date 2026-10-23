package app.masroufy.ui.shell

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Category
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.ruleFor
import app.masroufy.ui.app.LocalDataChanges
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SegmentedTabs
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.overlay.Veil
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.AddKind
import app.masroufy.usecase.AddOperationDraft
import app.masroufy.usecase.AddOperationOptions
import app.masroufy.usecase.AddOperationResult
import kotlinx.coroutines.launch

/**
 * لوحة «+» ⇒ «عملية جديدة» (`AddSheet`/`AddOperation` جوه `BottomBar` في النموذج): صرف · دخل · تحويل، المبلغ بلون النوع، التصنيف (أو نوع الدخل،
 * أو «إلى أين؟» للتحويل) **في مكان ثابت الطول (3 صفوف — [ADD_CHIP_AREA]) والزيادة بتتمرر جواه، عشان طول اللوحة ما يتغيرش بين التبويبات**، و«من أين؟» (المحفظة الأساسية مختارة لوحدها — ولو لسه
 * مفيش أساسية: فاضية و«من أين تصرف عادةً؟»، `MainWallet.kt`)، و«احفظ» (معطّل لحد ما الأساسي يكمل — والمحفظة منه).
 * الحفظ ⇒ `QuickAddOperation` (`AddTransaction`) ⇒ اللوحة تقفل ورسالة «سُجّلت: …» و**الشاشة اللي تحتها تقرا تاني** (`DataChanges` — الرئيسية
 * بتفضل ظاهرة تحت اللوحة، فمن غيرها «معك الآن» و«صرفت هذا الشهر» كانوا بيفضلوا قدام لحد ما تسيب التبويب). الخطأ جنب المبلغ.
 * «بصوتك» ⇒ [onVoice]: اللوحة بتتقفل والمساعد بيتفتح ويسمعك هناك (رد المالك OVERRIDES §82-2 — نفس ميكروفون الشريط تحت).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddOperationSheet(visible: Boolean, onClose: () -> Unit, onVoice: () -> Unit) {
    val space = LocalSpace.current
    val shell = space.shell
    val toaster = LocalToaster.current
    val changes = LocalDataChanges.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf<AddOperationOptions?>(null) }
    var kind by remember { mutableStateOf(AddKind.OUT) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<Category?>(null) }
    var income by remember { mutableStateOf<app.masroufy.core.EconomicKind?>(null) }
    var from by remember { mutableStateOf<String?>(null) }
    var to by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf<String?>(null) } // r06-links: null = النهارده

    // فيه محفظة أساسية للبلد دي وقت ما اللوحة اتفتحت؟ لأ ⇒ «بتصرف عادةً منين؟» واللي يختاره بيبقى الأساسي بعد الحفظ (المحرك —
    // `ShellDeps.addWalletDefault` على الحساب لكل بلد · `MainWallet.kt`)
    var hadMain by remember { mutableStateOf(false) }
    var askPrompt by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(visible, shell) {
        if (!visible) return@LaunchedEffect
        kind = AddKind.OUT; amountText = ""; category = null; income = null; error = null; date = null
        val start = app.masroufy.perf.PerfTrace.mark()
        val o = shell.addOptions()
        options = o
        val d = runCatching { shell.addWalletDefault() }.getOrNull()
        app.masroufy.perf.PerfTrace.log("span sheet:add ms=${start.elapsedNow().inWholeMilliseconds}")
        from = initialFromWallet(d?.wallet?.id, o.wallets)
        hadMain = from != null
        askPrompt = d?.prompt
        to = null
    }
    val o = options
    // r041b P7: «احفظ» المعطّل بيقول الناقص لما يتضغط (قبل كده ولا حاجة — والمالك ما يعرفش ليه)
    val missing = addMissing(kind, amountText, from, category != null, income != null, to)
    val ready = missing == null
    var explain by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { explain = false }
    Sheet(visible, onClose, title = t(UiKey.ADD_TITLE), veil = Veil.MENU, closeLabel = t(UiKey.SHELL_CLOSE)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText(t(UiKey.ADD_TITLE), style = Type.section())
            VoiceButton(onVoice)
        }
        SegmentedTabs(
            listOf(AddKind.OUT to t(UiKey.ADD_TYPE_OUT), AddKind.IN to t(UiKey.ADD_TYPE_IN), AddKind.MOVE to t(UiKey.ADD_TYPE_MOVE)),
            kind, { kind = it; error = null },
        )
        val tone = when (kind) {
            AddKind.OUT -> AmountTone.EXPENSE
            AddKind.IN -> AmountTone.INCOME
            AddKind.MOVE -> AmountTone.TRANSFER
        }
        TextInput(
            amountText, { amountText = it; error = null }, label = t(UiKey.ADD_AMOUNT), placeholder = "0.00", ltr = true, keyboard = KeyboardType.Decimal,
            imeAction = ImeAction.Done, height = 60.dp, textSize = 28, error = error,
            unit = o?.currency?.let(::currencySymbol),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(t(when (kind) { AddKind.OUT -> UiKey.ADD_CATEGORY; AddKind.IN -> UiKey.ADD_INCOME_KIND; AddKind.MOVE -> UiKey.ADD_TO }))
            // r041 A1: ارتفاع **ثابت** في كل التبويبات (مش حد أدنى) والزيادة بتتمرر جواه — من غيره اللوحة كانت بتطول وتقصر مع التبويب
            // وصف التبويبات و«بصوتك» بيتنقلوا، فالضغطة على مكان التبويب القديم كانت بتفتح المساعد
            val chipScroll = rememberScrollState()
            LaunchedEffect(kind) { chipScroll.scrollTo(0) }
            FlowRow(Modifier.fillMaxWidth().height(ADD_CHIP_AREA).verticalScroll(chipScroll), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // كل نوع في مجموعة بمفتاحه وكل شريحة بمفتاحها: من غيرهم تبديل «صرف/دخل/تحويل» كان بيقرا خانة «remember» غلط
                // جوه «SelectChip» ⇒ ClassCastException والبرنامج يقع لما تدوس «تحويل» (إصدار 0.4)
                key(kind) {
                    when (kind) {
                        AddKind.OUT -> for (c in o?.categories.orEmpty()) key(c.id) {
                            SelectChip(c.name, category?.id == c.id, { category = c }, dot = parseHex(c.lightColor))
                        }
                        AddKind.IN -> for (k in o?.incomeKinds.orEmpty()) key(k) { SelectChip(ruleFor(k).label, income == k, { income = k }) }
                        AddKind.MOVE -> for (w in o?.wallets.orEmpty().filter { it.id != from }) key(w.id) { SelectChip(w.name, to == w.id, { to = w.id }) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // الدخل بيدخل محفظة ⇒ «إلى أين؟» — مش سؤال الصرف «من أين تصرف عادةً؟» (سموك r05)
            val walletLabel = when {
                kind == AddKind.IN -> t(UiKey.ADD_TO)
                hadMain -> t(fromLabelKey(true))
                else -> askPrompt ?: t(fromLabelKey(false))
            }
            FieldLabel(walletLabel, Modifier.weight(1f))
            for (w in o?.wallets.orEmpty().take(3)) key(w.id) { SelectChip(w.name, from == w.id, { from = w.id; if (to == w.id) to = null }) }
        }
        if (o != null && o.wallets.isEmpty()) FieldError(t(UiKey.ADD_NO_WALLET))
        // r06-links: التاريخ — افتراضيه النهارده، ونفس خانة التاريخ اللي في باقي البرنامج (`DateField`)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(t(app.masroufy.core.R06Key.ADD_DATE))
            app.masroufy.ui.screens.people.DateField(date ?: shell.today(), shell.today(), { date = it; error = null }, Modifier.weight(1f))
        }
        PrimaryButton(
            if (saving) t(UiKey.ADD_SAVING) else t(UiKey.ADD_SAVE),
            onClick = {
                if (!ready || saving) return@PrimaryButton
                saving = true
                scope.launch {
                    val draft = AddOperationDraft(kind, amountText, from, to, category?.id, income, name = category?.name.orEmpty(), date = date)
                    when (val r = shell.addOperation(draft)) {
                        is AddOperationResult.Invalid -> error = r.message
                        is AddOperationResult.Saved -> {
                            val label = category?.name ?: income?.let { ruleFor(it).label }
                            val saved = t(UiKey.ADD_SAVED, amountLabel(r.transaction.amountMinor, r.transaction.currency, tone) + (label?.let { " · $it" } ?: ""))
                            // أول مصروف من غير أساسية ⇒ اللي اختاره بقى الأساسي للبلد على الحساب («وصار «البنك» الأساسي» — النموذج)
                            val firstMain = from?.takeIf { !hadMain && kind == AddKind.OUT }
                                ?.takeIf { id -> runCatching { shell.setMainWallet(id) }.isSuccess }
                            val mainName = firstMain?.let { id -> o?.wallets?.firstOrNull { it.id == id }?.name }
                            toaster.show(if (mainName != null) t(UiKey.ADD_SAVED_MAIN, saved, mainName) else saved)
                            changes.changed()
                            onClose()
                        }
                    }
                    saving = false
                }
            },
            enabled = ready, loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth(),
            onDisabledClick = { explain = true },
        )
        // مفيش محافظ خالص ⇒ «ضيف محفظة» فوق كفاية (من غير سطر تاني يقول «اختار المحفظة»)
        if (explain && missing != null && (missing != UiKey.ADD_NEED_FROM || o?.wallets?.isNotEmpty() == true)) FieldError(t(missing))
    }
}

/**
 * r041b P7: أول حاجة ناقصة عشان «احفظ» يشتغل — بنفس ترتيب اللوحة من فوق لتحت: المبلغ ⇒ التصنيف (صرف) / نوع الدخل (دخل) / المحفظة
 * اللي رايح لها (تحويل) ⇒ «من أين؟». null = جاهز.
 */
internal fun addMissing(kind: AddKind, amountText: String, from: String?, hasCategory: Boolean, hasIncome: Boolean, to: String?): UiKey? = when {
    amountText.isBlank() -> UiKey.ADD_NEED_AMOUNT
    kind == AddKind.OUT && !hasCategory -> UiKey.ADD_NEED_CATEGORY
    kind == AddKind.IN && !hasIncome -> UiKey.ADD_NEED_INCOME_KIND
    kind == AddKind.MOVE && (to == null || to == from) -> UiKey.ADD_NEED_TO
    from == null -> UiKey.ADD_NEED_FROM
    else -> null
}

@Composable
private fun VoiceButton(onClick: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier.height(40.dp).pressScale(press).clip(RoundedCornerShape(18.dp)).background(Ink.selected).tap(press, onClick = onClick).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(Lucide.MIC, size = 18.dp, tint = Ink.primary)
        BasicText(t(UiKey.ADD_VOICE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
    }
}

/** لون التصنيف المتخزن (`#A36A21`) ⇒ لون — للنقطة بس (المبلغ ما بيورثش لون التصنيف). */
fun parseHex(hex: String): Color? {
    val h = hex.removePrefix("#")
    if (h.length != 6) return null
    return h.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}

/** r041 A1: صفوف مكان الشرايح الثابت في لوحة «+» (التصنيفات الأكتر بتتمرر جواه). */
internal const val ADD_CHIP_ROWS = 3

/** طول [rows] صفوف شرايح (40) بينهم فواصل (8) — نفس الطول في صرف ودخل وتحويل. */
internal fun chipAreaHeight(rows: Int, chip: Dp = 40.dp, gap: Dp = 8.dp): Dp = chip * rows + gap * (rows - 1)

/** مكان الشرايح في لوحة «+»: ثابت (مش حد أدنى) عشان وصف التبويبات و«بصوتك» ما يتنقلوش مع التبويب. */
internal val ADD_CHIP_AREA: Dp = chipAreaHeight(ADD_CHIP_ROWS)
