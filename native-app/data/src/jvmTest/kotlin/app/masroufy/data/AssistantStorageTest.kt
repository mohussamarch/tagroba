package app.masroufy.data

import app.masroufy.core.ASSISTANT_ALL_BACKUP_GROUPS
import app.masroufy.core.ASSISTANT_TOPICS_GROUP
import app.masroufy.core.AlertDismissal
import app.masroufy.core.AssistChoiceKind
import app.masroufy.core.AssistConversation
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistMessageKind
import app.masroufy.core.AssistOption
import app.masroufy.core.AssistScreen
import app.masroufy.core.AssistSpeaker
import app.masroufy.core.AssistTopic
import app.masroufy.core.BACKUP_GROUPS
import app.masroufy.core.CardState
import app.masroufy.core.Currency
import app.masroufy.core.ForgottenMark
import app.masroufy.core.LATER_BACKUP_GROUPS
import app.masroufy.core.MainWalletSource
import app.masroufy.core.ScreenLink
import app.masroufy.core.SplitDraft
import app.masroufy.core.SplitShare
import app.masroufy.core.TxnDraft
import app.masroufy.core.UnknownQuestion
import app.masroufy.core.UserSetting
import app.masroufy.core.checkFullBackupData
import app.masroufy.core.emptyBackupData
import app.masroufy.core.exportedBackupData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * تخزين المساعد (§78 + ردود المالك 2026-10-09): كل محوّل بيرجّع اللي اتكتب بالظبط · النص الحر بيتقص (CLAUDE.md #11) · النسخة الشاملة:
 * المجموعات الجديدة **ما بتتكتبش لو فاضية** (ملف حساب ما استخدمش المساعد هو هو زي التطبيق القديم) وبتتكتب لوحدها لو فيها حاجة،
 * والتطبيق القديم بيقرا مجموعاته بس (`src/domain/checkFullBackup.ts` بيلف على `BACKUP_GROUPS` بتاعته). بيانات مخترعة.
 */
class AssistantStorageTest {
    private val draft = TxnDraft(
        "قهوة", 1_500, Currency.SAR, "2026-10-10", "w-bank", "الراجحي", "c-coffee", "قهوة", "m-marsa", "مقهى المرسى",
        recurringItemId = "r-elec", similarTransactionId = "t-9", categoryChanged = true, transactionId = "t-10",
    )
    private val split = SplitDraft(
        "عشاء", 36_000, Currency.SAR, "2026-10-10", "w-bank", "الراجحي", null, null,
        listOf(SplitShare(null, "أنت", 12_000, isMe = true), SplitShare("p-ahmed", "أحمد", 12_000), SplitShare(null, "شخص 3", 12_000)),
    )
    private val message = AssistMessage(
        "conv-1-0001-m-1", "conv-1", "2026-10-10T09:00:00.000Z", AssistSpeaker.BOT, AssistMessageKind.TXN_CARD, "أسجّلها كده؟",
        topic = "action.quick_add", subjectId = "m-marsa", openerKey = "REC_1", state = CardState.PENDING, card = draft, split = split,
        options = listOf(AssistOption("w-bank", "الراجحي")), picked = "w-bank",
        links = listOf(ScreenLink.of(AssistScreen.CATEGORY_BUDGET, "categoryId" to "c-coffee")), choice = AssistChoiceKind.PICK_SUBJECT,
        payload = mapOf("text" to "قسّم 360 مع أحمد"), approximate = true,
    )

    private fun <T> roundTrip(codec: DocCodec<T>, value: T) = assertEquals(value, codec.decode(codec.toStore(value)))

    @Test fun everyCodecReturnsWhatItWrote() {
        roundTrip(AssistantCodecs.userSettings, UserSetting.MainWallet("egypt", "w-cib", MainWalletSource.WALLET_DETAIL, "2026-10-10T09:00:00.000Z"))
        roundTrip(AssistantCodecs.userSettings, UserSetting.Assistant(false, "2026-10-10T09:00:00.000Z"))
        roundTrip(AssistantCodecs.userSettings, UserSetting.Assistant(true, "2026-10-10T09:00:00.000Z"))
        roundTrip(AssistantCodecs.conversations, AssistConversation("conv-1", "default", "2026-10-10T09:00:00.000Z", "2026-10-10T09:01:00.000Z", "كم صرفت؟", "صرفت 85 ر.س", 2))
        roundTrip(AssistantCodecs.messages, message)
        roundTrip(AssistantCodecs.messages, message.copy(card = null, split = null, options = emptyList(), links = emptyList(), payload = emptyMap(), state = null, choice = null))
        roundTrip(AssistantCodecs.topics, AssistTopic("data.spend.category", "c-coffee", 3, "2026-10-10T09:00:00.000Z"))
        roundTrip(AssistantCodecs.forgotten, ForgottenMark("fact:store:m-marsa", "2026-10-10T09:00:00.000Z"))
        roundTrip(AssistantCodecs.unknown, UnknownQuestion("أخبار السوق", "اخبار السوق", 2, "2026-10-09T09:00:00.000Z", "2026-10-10T09:00:00.000Z", "home", "default"))
        roundTrip(AssistantCodecs.alertDismissals, AlertDismissal("default:bill|r-elec|2026-10-03", "2026-10-10T09:00:00.000Z"))
    }

    @Test fun learningOnIsOnlyWrittenWhenOff() {
        assertFalse("learningOn" in AssistantCodecs.userSettings.toStore(UserSetting.Assistant(true, "x")))
        assertEquals(false, AssistantCodecs.userSettings.toStore(UserSetting.Assistant(false, "x"))["learningOn"])
    }

    @Test fun longNumbersInFreeTextAreMasked() {
        val stored = AssistantCodecs.messages.toStore(message.copy(text = "حوّلت من حساب 1234567890123"))
        assertEquals("حوّلت من حساب ****0123", stored["text"])
        val conv = AssistantCodecs.conversations.toStore(AssistConversation("c", "default", "t", "t", "رقم 55554444333", "", 1))
        assertFalse((conv["title"] as String).contains("55554444333"))
    }

    @Test fun aLinkToAScreenFromANewerAppIsSkippedNotTheMessage() {
        val stored = AssistantCodecs.messages.toStore(message).toMutableMap()
        stored["links"] = listOf(mapOf("screen" to "nav.future_screen", "args" to emptyMap<String, String>(), "label" to "x"))
        val back = AssistantCodecs.messages.decode(stored)
        assertTrue(back.links.isEmpty())
        assertEquals(message.text, back.text)
    }

    /** حساب ما استخدمش المساعد ⇒ الملف من غير المجموعات الجديدة خالص (زي ملف التطبيق القديم بالظبط). */
    @Test fun emptyAssistantGroupsAreNotWrittenToTheFile() {
        val data = emptyBackupData()
        val exported = exportedBackupData(data)
        ASSISTANT_ALL_BACKUP_GROUPS.forEach { assertFalse(it in exported.keys, it) }
        data.getValue(ASSISTANT_TOPICS_GROUP) += AssistantCodecs.topics.toStore(AssistTopic("data.remaining", null, 2, "2026-10-10T09:00:00.000Z"))
        val withTopic = exportedBackupData(data)
        assertTrue(ASSISTANT_TOPICS_GROUP in withTopic.keys)
        assertEquals(listOf(ASSISTANT_TOPICS_GROUP), ASSISTANT_ALL_BACKUP_GROUPS.filter { it in withTopic.keys }, "كل مجموعة لوحدها")
    }

    /** النسخة الشاملة بكل المجموعات الجديدة بتعدّي الفحص، وملف التطبيق القديم (من غيرها) بيتقري (بتتكمّل فاضية). */
    @Test fun fullBackupAcceptsTheNewGroupsAndOldFiles() {
        assertTrue(ASSISTANT_ALL_BACKUP_GROUPS.all { it in BACKUP_GROUPS && it in LATER_BACKUP_GROUPS })
        val data = emptyBackupData()
        data.getValue(AssistantCodecs.userSettings.group) += AssistantCodecs.userSettings.toStore(UserSetting.MainWallet("default", "w-bank", MainWalletSource.CHAT, "t"))
        data.getValue(AssistantCodecs.conversations.group) += AssistantCodecs.conversations.toStore(AssistConversation("conv-1", "default", "t", "t", "س", "ج", 1))
        data.getValue(AssistantCodecs.messages.group) += AssistantCodecs.messages.toStore(message)
        data.getValue(AssistantCodecs.forgotten.group) += AssistantCodecs.forgotten.toStore(ForgottenMark("card:bill:r-elec:2026-10-03", "t"))
        data.getValue(AssistantCodecs.unknown.group) += AssistantCodecs.unknown.toStore(UnknownQuestion("س", "س", 1, "t", "t", "home", "default"))
        data.getValue(AssistantCodecs.alertDismissals.group) += AssistantCodecs.alertDismissals.toStore(AlertDismissal("a|b", "t"))
        checkFullBackupData(data)
        val old = data.filterKeys { it !in LATER_BACKUP_GROUPS }
        checkFullBackupData(old + LATER_BACKUP_GROUPS.associateWith { mutableListOf() })
    }
}
