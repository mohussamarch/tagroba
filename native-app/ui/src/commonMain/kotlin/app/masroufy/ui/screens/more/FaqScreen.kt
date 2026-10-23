package app.masroufy.ui.screens.more

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.FaqChrome
import app.masroufy.core.FaqSection
import app.masroufy.core.faqFor
import app.masroufy.core.faqSearch
import app.masroufy.core.faqSections
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «الأسئلة الشائعة» (`Faq.dc.html` — فرع التصميم 9170906d): بحث + شرايح الأقسام التسعة + أسئلة بتتفتح (إجابة سطر أو كذا سطر).
 * الأسئلة من بلد المساحة (`faqFor`: مصر 39 · السعودية 41 — تابي وتمارا للسعودية بس) والنص من `core/FaqContent*.kt`.
 * البحث شغال ⇒ كل النتايج مفتوحة. «مالقيتش إجابتك؟» بيفتح «ابعتلنا» وكلمة البحث مكتوبة فيه.
 * ⚠️ شريط النصيحة (`TipStrip`) لسه ما اتنقلش (مفيش مكوّن ليه في كوتلن) — جملته بتظهر سطر عادي.
 */
@Composable
fun FaqScreen() {
    val code = LocalSpace.current.space.countryCode
    val egypt = code.equals("EG", ignoreCase = true)
    val chrome = remember(egypt) { FaqChrome(egypt) }
    val items = remember(code) { faqFor(code) }
    val sections = remember(items) { faqSections(items) }
    var query by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(-1) }
    var open by rememberSaveable { mutableStateOf(-1) }
    var asking by rememberSaveable { mutableStateOf(false) }
    val searching = query.isNotBlank()
    val shown = faqSearch(items, query).filter { section < 0 || it.section.ordinal == section }
    // بيانات الاستخدام (PLAN-0.6 §16 «الاحتكاك»): بحث من غير نتيجة بعد ما الكتابة تهدى — **العدد بس**، النص عمره ما بيتبعت
    val noResult = searching && shown.isEmpty()
    androidx.compose.runtime.LaunchedEffect(query, noResult) {
        if (!noResult) return@LaunchedEffect
        kotlinx.coroutines.delay(FAQ_SEARCH_SETTLE_MS)
        app.masroufy.problems.Usage.log(app.masroufy.core.UsageEvents.faqNoResult())
    }
    InnerScaffold(chrome.title) {
        item(key = "tip") { BasicText(chrome.tip, style = Type.caption().copy(color = Ink.muted)) }
        item(key = "search") { TextInput(query, { query = it }, placeholder = chrome.searchHint, draft = false) }
        item(key = "chips") { SectionChips(chrome, sections, section, egypt) { section = it } }
        for (s in sections) {
            val rows = shown.filter { it.section == s }
            if (rows.isEmpty()) continue
            item(key = "group-${s.name}") { FaqGroup(s.label(egypt), rows, open, searching) { id -> open = if (open == id) -1 else id } }
        }
        if (shown.isEmpty()) item(key = "empty") {
            BasicText(chrome.empty, Modifier.fillMaxWidth(), style = Type.of(14).copy(color = Ink.muted, textAlign = TextAlign.Center))
        }
        item(key = "ask") {
            if (!asking) TonalButton(chrome.ask, {
                asking = true
                app.masroufy.problems.Usage.log(app.masroufy.core.UsageEvents.faqNotAnswered())
            }, Modifier.fillMaxWidth(), height = 52.dp)
            else SupportForm(egypt, prefill = query)
        }
    }
}

/** البحث من غير نتيجة بيتعد بعد ما الكتابة تقف المدة دي (مش كل حرف). */
private const val FAQ_SEARCH_SETTLE_MS = 1500L

@Composable
private fun SectionChips(chrome: FaqChrome, sections: List<FaqSection>, selected: Int, egypt: Boolean, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectChip(chrome.all, selected < 0, { onPick(-1) })
        for (s in sections) SelectChip(s.label(egypt), selected == s.ordinal, { onPick(s.ordinal) })
    }
}
