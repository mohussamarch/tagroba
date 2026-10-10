package app.masroufy.ui.screens.investment.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.masroufy.core.EstateOwner
import app.masroufy.core.InheritanceLaw
import app.masroufy.core.InheritanceResult
import app.masroufy.core.InvalidReason
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.screens.investment.InheritanceSavedRoute
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * «حاسبة الورث» (لوحة `InheritanceCalculator`): خطوة بخطوة — تركة مَن ⇒ الأملاك ⇒ الورثة (`InheritanceHeirs`) ⇒ قبل القسمة
 * (`InheritanceBefore`) ⇒ النتيجة (`InheritanceResult` + `InheritanceSplit`) ⇒ «احفظ الحسبة». القانون من البلد تلقائيًا (والمحفوظة بقانون بلدها).
 */
@Composable
fun InheritanceCalculatorScreen(scenarioId: String?) {
    val space = LocalSpace.current
    val deps = space.investment.calculators
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val today = remember(deps) { deps.today() }
    val active = space.space.countryCode.uppercase()
    var encoded by rememberSaveable(scenarioId) { mutableStateOf(encodeDraft(InheritanceDraft(active))) }
    var loaded by rememberSaveable(scenarioId) { mutableStateOf(scenarioId == null) }
    val draft = decodeDraft(encoded) ?: InheritanceDraft(active)
    val update = { d: InheritanceDraft -> encoded = encodeDraft(d) }
    var people by remember(deps) { mutableStateOf<List<PersonChoice>>(emptyList()) }
    LaunchedEffect(deps) { people = runCatching { deps.people() }.getOrDefault(emptyList()) }
    LaunchedEffect(deps, scenarioId, loaded) {
        if (loaded || scenarioId == null) return@LaunchedEffect
        val s = runCatching { deps.scenarios.load(scenarioId) }.getOrNull()
        if (s != null) update(draftFrom(s, runCatching { deps.people() }.getOrDefault(emptyList())))
        else toaster.show(t(TextKey.INHERIT_SCENARIO_NOT_FOUND), dark = true)
        loaded = true
    }
    var stepError by remember { mutableStateOf<String?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val foreign = !draft.countryCode.equals(active, ignoreCase = true)
    val result = remember(encoded, deps) {
        if (draft.step != 5) null
        else {
            val calc = if (foreign) deps.inheritanceUnder(draft.countryCode) else deps.inheritance
            val case = draft.toCase()
            if (case == null) badAmountResultUi(draft.law, draft.currency)
            else {
                // المحرك بيرمي بس لو الأرقام أكبر من المسموح (فيضان) ⇒ نفس رسالته
                val r = runCatching { calc.calculate(case) }.getOrNull() ?: InheritanceResult.Invalid(InvalidReason.TOO_LARGE, law = draft.law)
                inheritanceResultUi(r, draft.currency)
            }
        }
    }
    val go = { step: Int ->
        stepError = null
        update(draft.copy(step = step))
    }

    Box(Modifier.fillMaxSize()) {
        InnerScaffold(t(TextKey.INHCALC_TITLE), actions = { SavedButton { nav.push(InheritanceSavedRoute) } }) {
            item(key = "law") { LawLines(draft, foreign) }
            item(key = "steps") { StepsBar(draft.step) { k -> if (k < draft.step) go(k) } }
            // حسبة محفوظة لسه بتتقري ⇒ هيكل مكان النتيجة (مش خطوة «تركة مَن» للحظة)
            if (!loaded) item(key = "loading") { Skeleton(Modifier.fillMaxWidth().height(220.dp), radius = 28.dp) }
            else when (draft.step) {
                1 -> item(key = "whose") { WhoseStep(draft, people) { stepError = null; update(it) } }
                2 -> item(key = "items") {
                    ItemsStep(draft, canBring = draft.estateOf == EstateOwner.MINE && !foreign, onChange = { stepError = null; update(it) }) {
                        scope.launch {
                            val got = runCatching { deps.inheritance.prefillMyEstate() }.getOrDefault(emptyList())
                            val have = draft.items.map { it.name.trim() }.toSet()
                            val add = got.filter { it.name.trim() !in have }.map { EstateRow(it.name, it.valueMinor?.let { v -> plainAmount(v, draft.currency) }.orEmpty()) }
                            update(draft.copy(items = draft.items + add))
                            toaster.show(
                                when {
                                    add.isNotEmpty() -> t(TextKey.INHCALC_BROUGHT, thingsPhrase(add.size))
                                    got.isEmpty() -> t(TextKey.INHCALC_BRING_NONE)
                                    else -> t(TextKey.INHCALC_BRING_ALREADY)
                                },
                            )
                        }
                    }
                }
                3 -> item(key = "heirs") { InheritanceHeirsStep(draft) { update(it) } }
                4 -> item(key = "before") { InheritanceBeforeStep(draft) { stepError = null; update(it) } }
                else -> item(key = "result") {
                    result?.let { InheritanceResultPanel(it) { yes -> update(draft.copy(distantRelatives = yes)) } }
                }
            }
            stepError?.let { e -> item(key = "error") { BasicText(e, style = Type.captionBold().copy(color = Ink.expense)) } }
            item(key = "bottom-space") { Spacer(Modifier.height(96.dp)) }
        }
        if (loaded) BottomActions(
            Modifier.align(Alignment.BottomCenter),
            backLabel = if (draft.step > 1) t(if (draft.step == 5) TextKey.INHCALC_EDIT else TextKey.INHCALC_PREV) else null,
            onBack = { go(if (draft.step == 5) 3 else draft.step - 1) },
            mainLabel = t(
                when (draft.step) {
                    5 -> TextKey.INHCALC_SAVE
                    4 -> TextKey.INHCALC_CALC
                    else -> TextKey.INHCALC_NEXT
                },
            ),
        ) {
            if (draft.step == 5) {
                saveName = defaultScenarioName(draft, today)
                saveError = null
                sheetOpen = true
            } else {
                val e = checkStep(draft)
                if (e != null) stepError = e else go(draft.step + 1)
            }
        }
    }
    SaveSheet(sheetOpen, saveName, saveError, saving, { saveName = it.take(60); saveError = null }, { sheetOpen = false }) {
        val sd = draft.toScenarioDraft(saveName.trim().ifEmpty { defaultScenarioName(draft, today) }) ?: return@SaveSheet
        saving = true
        scope.launch {
            try {
                val s = deps.scenarios.save(sd, draft.scenarioId)
                update(draft.copy(scenarioId = s.id, scenarioName = s.name))
                sheetOpen = false
                toaster.show(t(TextKey.INHCALC_SAVED_TOAST, s.name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                saveError = e.message
            } finally {
                saving = false
            }
        }
    }
}

/** تحت العنوان: تركة مين، والقانون (+ «حسبة محفوظة من …» لو من بلد تانية). */
@Composable
private fun LawLines(d: InheritanceDraft, foreign: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val whose = when {
            d.step == 1 -> t(TextKey.INHCALC_WHOSE_LINE_START)
            d.estateOf == EstateOwner.MINE -> t(TextKey.INHCALC_MINE)
            else -> t(TextKey.INHCALC_OF, d.personName.ifBlank { t(TextKey.INHCALC_OTHER) })
        }
        BasicText(whose, style = Type.caption().copy(color = Ink.muted))
        val law = t(if (d.law == InheritanceLaw.EG) TextKey.INHCALC_LAW_EG else TextKey.INHCALC_LAW_SA) +
            if (foreign) t(TextKey.INHCALC_LAW_FOREIGN, t(if (d.law == InheritanceLaw.EG) TextKey.INHCALC_COUNTRY_EG else TextKey.INHCALC_COUNTRY_SA)) else ""
        BasicText(
            law,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.selected).padding(horizontal = 12.dp, vertical = 8.dp),
            style = Type.caption().copy(color = Ink.primary),
        )
    }
}

/** الشريط تحت: «السابق»/«عدّل» + الزرار الأساسي، فوق تدرّج بيغطي آخر المحتوى. */
@Composable
private fun BottomActions(modifier: Modifier, backLabel: String?, onBack: () -> Unit, mainLabel: String, onMain: () -> Unit) {
    Row(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color(0x00EFF3ED), 0.3f to Color(0xF0EFF3ED)))
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (backLabel != null) SecondaryButton(backLabel, onBack, Modifier.weight(1f), height = 52.dp)
        PrimaryButton(mainLabel, onMain, Modifier.weight(2f), height = 52.dp)
    }
}
