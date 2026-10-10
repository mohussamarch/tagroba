package app.masroufy.usecase

import app.masroufy.core.ArabicVariant
import app.masroufy.core.FactKeys
import app.masroufy.core.Language
import app.masroufy.core.LearnedKind
import app.masroufy.core.Texts
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * «اللي اتعلمته عنك» (§78-٤ + النموذج): الحاجات المؤكدة **من بياناتك المؤكدة بس** — عمرها ما بتيجي من سؤال؛ والمواضيع من مرتين. كل عنصر
 * بيتمسح لوحده، و«امسح الكل»، ومفتاح التعلم. بيانات مخترعة.
 */
class AssistantMemoryTest {
    @BeforeTest fun msa() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test fun confirmedFactsComeFromDataNotFromQuestions() = runBlocking<Unit> {
        val w = AssistantWorld()
        val items = AssistantMemory(w.deps).list(w.ctx()).items
        assertEquals(setOf(FactKeys.salary("i-job", 28), FactKeys.store("m-marsa")), items.filter { it.kind == LearnedKind.FACT }.map { it.key }.toSet())
        // أسئلة كتير عن محل تاني ما بتعملش «حقيقة» — بتبقى موضوع بس
        repeat(3) { w.chat.send("صرفت كام على البقالة الشهر ده؟", w.ctx()) }
        val after = AssistantMemory(w.deps).list(w.ctx()).items
        assertEquals(2, after.count { it.kind == LearnedKind.FACT })
        assertEquals(listOf(3), after.filter { it.kind == LearnedKind.TOPIC }.map { it.askCount })
    }

    @Test fun unconfirmedTransactionsTeachNothing() = runBlocking<Unit> {
        val w = AssistantWorld()
        val atStore = w.txns.listByDateRange("2000-01-01", "2100-01-01").filter { it.merchantId != null }
        w.txns.deleteMany(atStore.map { it.id })
        w.txns.saveMany(atStore.map { it.copy(economicKindConfirmed = false) })
        assertFalse(AssistantMemory(w.deps).list(w.ctx()).items.any { it.key == FactKeys.store("m-marsa") })
    }

    @Test fun eachItemIsDeletableAloneAndClearAllWipesEverything() = runBlocking<Unit> {
        val w = AssistantWorld()
        val memory = AssistantMemory(w.deps)
        repeat(2) { w.chat.send("فاضلي كام؟", w.ctx()) }
        memory.forget(FactKeys.store("m-marsa"))
        val left = memory.list(w.ctx()).items.map { it.key }
        assertFalse(FactKeys.store("m-marsa") in left)
        assertTrue(FactKeys.salary("i-job", 28) in left)
        val topic = memory.list(w.ctx()).items.first { it.kind == LearnedKind.TOPIC }
        memory.forget(topic.key)
        assertTrue(memory.list(w.ctx()).items.none { it.kind == LearnedKind.TOPIC })
        repeat(2) { w.chat.send("فاضلي كام؟", w.ctx()) }
        memory.clearAll(w.today)
        assertTrue(memory.list(w.ctx()).items.isEmpty())
        memory.setLearning(false)
        assertFalse(memory.list(w.ctx()).learningOn)
    }
}
