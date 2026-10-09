package app.masroufy.ui.screens.imports

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.RowError
import app.masroufy.core.SchemaId
import app.masroufy.usecase.ImportCountsPreview
import app.masroufy.usecase.ImportImpact
import app.masroufy.usecase.ImportPreview
import app.masroufy.usecase.ImportPreviewLine
import app.masroufy.usecase.SmsBatch
import app.masroufy.usecase.SmsSkip
import app.masroufy.usecase.UnmappedSender
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «إعداد القراءة» (`BankSmsSettings`) و«إضافة من الرسائل» (`SmsPaste`): من نتايج حالات الاستخدام لحالة الشاشة. */
class SmsSettingsAndPasteTest {
    @AfterTest fun reset() = Fx.resetTexts()

    private val wallets = listOf(Fx.wallet("w1", "حساب الراتب"), Fx.wallet("w2", "حساب التوفير"), Fx.wallet("cash", "الكاش", kind = "cash"))

    @Test fun settingsWithoutAReaderIsUnavailable() {
        val ui = smsSettingsUi(null, wallets)
        assertFalse(ui.available)
        assertFalse(ui.reading)
    }

    @Test fun eachSenderShowsItsWalletOrHowManyMessagesWait() {
        val overview = SmsOverview(
            Fx.inbox(senders = listOf("RAJHI", "ALWAHA", "SNB")),
            unmapped = listOf(UnmappedSender("sa", "alwaha", 2)),
            reviews = emptyList(),
            senderWallets = mapOf("RAJHI" to "w1", "ALWAHA" to null, "SNB" to "gone"),
        )
        val ui = smsSettingsUi(overview, wallets)
        assertTrue(ui.reading)
        assertEquals(listOf("حساب الراتب", null, null), ui.senders.map { it.walletName }, "محفظة اتمسحت ⇒ مفيش اسم (غير متاح)، مش تخمين")
        assertEquals(listOf(0, 2, 0), ui.senders.map { it.waiting }, "المستني بنفس المرسل بأي حالة حروف")
        assertFalse(ui.full)
    }

    @Test fun permissionLostOnlyWhenReadingIsOnAndTenSendersIsFull() {
        val lost = smsSettingsUi(SmsOverview(Fx.inbox(permission = false), emptyList(), emptyList(), emptyMap()), wallets)
        assertTrue(lost.permissionLost)
        val off = smsSettingsUi(SmsOverview(Fx.inbox(enabled = false, permission = false), emptyList(), emptyList(), emptyMap()), wallets)
        assertFalse(off.permissionLost, "متوقفة بإيده ⇒ مش «سُحب الإذن»")
        val ten = smsSettingsUi(SmsOverview(Fx.inbox(senders = (1..10).map { "B$it" }), emptyList(), emptyList(), emptyMap()), wallets)
        assertTrue(ten.full, "أقصى ١٠ مرسلين")
    }

    @Test fun addingAndRemovingSendersIgnoresLetterCase() {
        assertEquals(listOf("RAJHI"), sendersWith(listOf("RAJHI"), " rajhi "))
        assertEquals(listOf("RAJHI", "SNB"), sendersWith(listOf("RAJHI"), " SNB "))
        assertEquals(listOf("SNB"), sendersWithout(listOf("RAJHI", "SNB"), "rajhi"))
    }

    private val batch = SmsBatch(
        rows = listOf(Fx.smsRow(1, " سوبرماركت الحي ", 8650), Fx.smsRow(3, "مقهى المرسى", 2300), Fx.smsRow(4, "صيدلية", 6400, Direction.IN)),
        skipped = listOf(SmsSkip(2, "رسالة تحقق"), SmsSkip(5, "عرض"), SmsSkip(6, "رسالة تحقق")),
        content = "[]",
        truncated = false,
    )

    private fun line(n: Int, state: MatchingState) = ImportPreviewLine(Fx.parsed(n, "x", 100), state, "سبب", categoryReason = "", selectedByDefault = state == MatchingState.NEW)

    @Test fun pastedMessagesArePendingUntilAWalletIsChosen() {
        val ui = smsReadUi(batch, null)
        assertEquals(List(3) { PastedState.PENDING }, ui.rows.map { it.state }, "منع التكرار بيتحسب على المحفظة ⇒ قبلها مش معروف")
        assertEquals(0, ui.toSave)
        assertEquals(6, ui.messages)
        assertEquals("سوبرماركت الحي", ui.rows[0].merchant)
        assertEquals(listOf("رسالة تحقق" to 2, "عرض" to 1), ui.skipped.map { it.reason to it.count })
        assertEquals(3, ui.skippedCount)
    }

    @Test fun afterThePreviewOnlyNewLinesAreSaved() {
        val preview = ImportPreview(
            "bank-sms.json", "hash", SchemaId.SMS, "حساب الراتب", previousBatch = null,
            lines = listOf(line(1, MatchingState.NEW), line(3, MatchingState.DUPLICATE), line(4, MatchingState.SIMILAR)),
            errors = emptyList<RowError>(), counts = ImportCountsPreview(3, 1, 1, 1, 0, 0), impact = ImportImpact(-8650, 8650, 0),
        )
        val ui = smsReadUi(batch, preview)
        assertEquals(listOf(PastedState.NEW, PastedState.DUPLICATE, PastedState.SIMILAR), ui.rows.map { it.state })
        assertEquals(1, ui.toSave)
        assertEquals(1, ui.duplicates)
        assertEquals(listOf(1), ui.selected, "الشبيه محتاج قرار في «مراجعة الكشف» — هنا ما بيتسجلش")
    }

    @Test fun theDefaultWalletIsTheOnlyBankAccount() {
        assertNull(defaultWallet(wallets), "حسابين بنك ⇒ المالك يختار")
        assertEquals("w1", defaultWallet(wallets.filter { it.id != "w2" })?.id, "الكاش ما بيتحسبش")
    }

    @Test fun pastedMessagesUseTheSameRequestShapeAsTheInbox() {
        val wallet = Fx.wallet("eg1", "QNB", currency = Currency.EGP)
        val r = smsRequest(batch, wallet)
        assertEquals(ImportSourceType.SMS, r.sourceType)
        assertEquals(SchemaId.SMS, r.schema)
        assertEquals("QNB", r.accountIdentity, "هوية الحساب = اسم المحفظة زي `ReviewSmsInbox`")
        assertEquals(Currency.EGP, r.currency, "عملة المحفظة، مش الريال")
        assertEquals(listOf(1, 3, 4), r.parsedRows?.map { it.lineNumber })
    }
}
