package app.masroufy.usecase

import app.masroufy.core.FeedbackDoc
import app.masroufy.core.SupportKind
import app.masroufy.core.SupportMessage
import app.masroufy.memory.MemorySupportOutbox
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.FeedbackInboxPort
import app.masroufy.port.FeedbackRejected
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** «ابعتلنا» (OVERRIDES §114): نسخة على الجهاز دايمًا · «اتبعت» بعد تأكيد السيرفر بس · المستنية بتتعاد بنفس المعرّف. */
class SendSupportMessageTest {
    private class FakeInbox : FeedbackInboxPort {
        var online = true
        var reject = false
        val written = LinkedHashMap<String, FeedbackDoc>()

        override suspend fun send(id: String, doc: FeedbackDoc) {
            if (reject) throw FeedbackRejected()
            if (!online) throw IllegalStateException("offline")
            written[id] = doc
        }
    }

    private val msg = SupportMessage(SupportKind.IDEA, "  فكرة حلوة  ", "2026-10-23", "EG")

    @Test fun sentOnlyAfterTheServerConfirms() = runBlocking {
        val outbox = MemorySupportOutbox()
        val inbox = FakeInbox()
        val use = SendSupportMessage(outbox, inbox, SequentialIdGenerator(), "kotlin-0.6")
        assertEquals(SupportSendResult.SENT, use.send(msg))
        assertEquals(listOf(FeedbackDoc("suggestion", "eg", "فكرة حلوة", "kotlin-0.6")), inbox.written.values.toList())
        assertTrue(outbox.pending().isEmpty())
        assertEquals(1, outbox.all().size)
    }

    @Test fun offlineIsQueuedThenRetriedWithTheSameId() = runBlocking {
        val outbox = MemorySupportOutbox()
        val inbox = FakeInbox().apply { online = false }
        val use = SendSupportMessage(outbox, inbox, SequentialIdGenerator(), "v")
        assertEquals(SupportSendResult.QUEUED, use.send(msg))
        assertEquals(SupportSendResult.QUEUED, use.send(msg.copy(kind = SupportKind.PROBLEM, countryCode = "SA")))
        val ids = outbox.pending().map { it.id }
        assertEquals(2, ids.size)
        assertEquals(0, use.flush())
        inbox.online = true
        assertEquals(2, use.flush())
        assertEquals(ids, inbox.written.keys.toList())
        assertEquals("problem", inbox.written.getValue(ids[1]).kind)
        assertEquals("sa", inbox.written.getValue(ids[1]).country)
        assertTrue(outbox.pending().isEmpty())
        assertEquals(2, outbox.all().size)
    }

    @Test fun rejectedIsDroppedFromPendingButKeptLocally() = runBlocking {
        val outbox = MemorySupportOutbox()
        val inbox = FakeInbox().apply { online = false }
        val use = SendSupportMessage(outbox, inbox, SequentialIdGenerator(), "v")
        use.send(msg)
        inbox.reject = true
        assertEquals(0, use.flush())
        assertTrue(outbox.pending().isEmpty())
        assertEquals(1, outbox.all().size)
    }

    @Test fun aHangingWriteIsQueuedAfterTheTimeoutAndRetriedLater() = runBlocking {
        val outbox = MemorySupportOutbox()
        val hang = object : FeedbackInboxPort {
            var stuck = true
            val written = LinkedHashMap<String, FeedbackDoc>()
            override suspend fun send(id: String, doc: FeedbackDoc) {
                if (stuck) kotlinx.coroutines.awaitCancellation()
                written[id] = doc
            }
        }
        val use = SendSupportMessage(outbox, hang, SequentialIdGenerator(), "v", sendTimeoutMs = 50)
        assertEquals(SupportSendResult.QUEUED, use.send(msg), "أوفلاين ⇒ «محفوظ» بعد الحد، مش بعد ما فايربيز يستسلم")
        assertEquals(1, outbox.pending().size)
        hang.stuck = false
        assertEquals(1, use.flush())
        assertEquals(1, hang.written.size)
        assertTrue(outbox.pending().isEmpty())
        assertEquals(5_000L, SUPPORT_SEND_TIMEOUT_MS)
    }

    @Test fun noFirebaseMeansSavedOnlyAndTextIsCapped() = runBlocking {
        val outbox = MemorySupportOutbox()
        val use = SendSupportMessage(outbox, null, SequentialIdGenerator(), "v")
        assertEquals(SupportSendResult.QUEUED, use.send(msg.copy(text = "x".repeat(2500))))
        assertEquals(2000, outbox.all().single().text.length)
    }
}
