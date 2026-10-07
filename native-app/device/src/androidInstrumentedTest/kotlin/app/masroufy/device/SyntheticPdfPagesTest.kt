package app.masroufy.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** PdfBox على كشف مخترع (`SyntheticStatement`) — نفس الاختبار اللي بيشتغل على الآيفون (PDFKit) على GitHub. */
@RunWith(AndroidJUnit4::class)
class SyntheticPdfPagesTest {
    @Test fun readsTheSyntheticStatementLikeTheWordsItWasWrittenWith() = runBlocking<Unit> {
        val progress = mutableListOf<Pair<Int, Int>>()
        val read = PdfBoxPages(InstrumentationRegistry.getInstrumentation().targetContext).read(SyntheticStatement.pdf()) { p, t -> progress += p to t }
        assertEquals(listOf(1 to 2, 2 to 2), progress)
        SyntheticStatement.assertReadsLikeTheWords(read)
    }
}
