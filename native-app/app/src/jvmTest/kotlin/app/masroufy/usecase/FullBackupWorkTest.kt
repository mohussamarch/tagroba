package app.masroufy.usecase

import app.masroufy.core.FullBackupData
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.port.FullBackupPort
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * مراجعة r06 arch-1 (ARCHITECTURE §31.42): عمل النسخة الكاملة واستعادتها (`create` · `plan` · `apply`) بيتنفذ على [FullBackup.work] — مش
 * على خيط الواجهة اللي ناداه. والحجم بالبايت من غير نسخة تانية من النص بيطابق UTF-8 بالظبط.
 */
class FullBackupWorkTest {
    private class Watched(private val inner: FullBackupPort) : FullBackupPort by inner {
        val seen = mutableListOf<String?>()

        override suspend fun read(): FullBackupData {
            seen += coroutineContext[CoroutineName]?.name
            return inner.read()
        }
    }

    @Test fun createPlanAndApplyRunOnTheWorkContext() = runBlocking<Unit> {
        val port = Watched(MemoryFullBackup())
        val backup = FullBackup(port, work = Dispatchers.Default + CoroutineName("backup-work"))
        val made = backup.createText("2026-10-22T10:00:00.000Z")
        val plan = backup.plan(made.text)
        backup.apply(plan.file)
        assertEquals(made.text.encodeToByteArray().size.toLong(), made.utf8Bytes)
        assertEquals(true, port.seen.isNotEmpty())
        assertEquals(listOf("backup-work"), port.seen.distinct(), "كل قراية للبيانات جوه سياق الشغل")
    }

    @Test fun utf8SizeMatchesTheEncodedBytes() {
        for (s in listOf("", "abc", "مصروفي 123", "€ ✓", "emoji 😀 end", "lone \uD800 high", "\uDC00 lone low")) {
            assertEquals(s.encodeToByteArray().size.toLong(), utf8Size(s), s)
        }
    }
}
