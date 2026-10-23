package app.masroufy.device

import app.masroufy.core.SupportKind
import app.masroufy.core.SupportMessage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** صندوق «ابعتلنا» في الملف: بيحفظ بنوعه وبيقراه زي ما هو (أسطر · تبويب · شرطة مايلة) — ومن غير إرسال لأي مكان. */
class SupportOutboxDeviceTest {
    private val dir: File = Files.createTempDirectory("support").toFile()
    private val file = File(dir, "support/outbox.log")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun emptyUntilSomethingIsSaved() {
        assertTrue(FileSupportOutbox(file).all().isEmpty())
    }

    @Test
    fun savesWithKindAndReadsBackInOrder() {
        val box = FileSupportOutbox(file)
        val a = SupportMessage(SupportKind.QUESTION, "الشهر عندي بيبدأ إمتى؟", "2026-10-23", "EG")
        val b = SupportMessage(SupportKind.PROBLEM, "سطر أول\nسطر تاني\twith tab and back\\slash", "2026-10-24", null)
        box.add(a, "id-a")
        box.add(b, "id-b")
        val back = FileSupportOutbox(file).all()
        assertEquals(listOf(a, b.copy(text = "سطر أول\nسطر تاني with tab and back\\slash")), back)
        assertEquals(2, file.readLines().size)
    }

    @Test
    fun pendingUntilSentAndTheLocalCopyStays() {
        val box = FileSupportOutbox(file)
        val a = SupportMessage(SupportKind.IDEA, "فكرة\nبسطرين", "2026-10-23", "SA")
        val b = SupportMessage(SupportKind.QUESTION, "سؤال", "2026-10-23", "EG")
        box.add(a, "id-a")
        box.add(b, "id-b")
        assertEquals(listOf("id-a", "id-b"), FileSupportOutbox(file).pending().map { it.id })
        assertEquals(a, box.pending().first().message)
        box.sent("id-a")
        assertEquals(listOf("id-b"), FileSupportOutbox(file).pending().map { it.id })
        box.sent("id-b")
        assertTrue(box.pending().isEmpty())
        assertEquals(2, box.all().size)
    }
}
