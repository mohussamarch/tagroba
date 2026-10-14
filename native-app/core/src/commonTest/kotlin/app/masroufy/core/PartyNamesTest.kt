package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * قايمة المالك §6.5 بند 8 — «الأسامي بتظهر أول كام حرف بس»: الاسم المعروض للطرف **كامل**، والمفتاح **زي ما كان بالظبط** (القرارات
 * المحفوظة بيه)، والاسم اللي البنك قصّه بيتعلّم [NameCut]. **كل الأسماء والأرقام مخترعة.**
 */
class PartyNamesTest {
    private var seq = 0

    private fun sms(body: String, egypt: Boolean = false): Transaction {
        val message = BankSmsMessage("TESTBANK", "2026-09-18T10:00:00Z", body)
        val row = ((if (egypt) parseEgyptBankSms(message, 1) else parseBankSms(message, 1)) as SmsParseResult.Ok).row
        return Transaction(
            id = "t-${seq++}", occurredAt = row.date, datePrecision = "day", sourceOrder = seq, economicKind = EconomicKind.UNCLASSIFIED,
            economicKindConfirmed = false, observedDirection = row.direction, amountMinor = row.amountMinor, currency = Currency.SAR,
            categoryConfirmed = false, excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x",
            updatedAt = "x", rawDescription = row.description, rawMerchantName = row.merchantName,
        )
    }

    private fun statement(op: String, desc: String, dir: Direction = Direction.IN) = Transaction(
        id = "s-${seq++}", occurredAt = "2026-01-10", datePrecision = "day", sourceOrder = seq, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = 10_000, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawDescription = desc, sourceOperationType = op,
    )

    private val internalOp = "عملية تحويل داخلية"

    @Test fun longArabicAndLatinNamesInBankSmsStayWhole() {
        val inc = transferPartyOf(sms("حوالة داخلية واردة\nبـSR 500\nلـ9010\nمن2134;عبدالرحمن بن محمد آل-سالم الحربي\n26/9/18 09:35"))!!
        assertEquals("عبدالرحمن بن محمد آل-سالم الحربي", inc.label)
        assertNull(inc.cut)
        val out = transferPartyOf(sms("حوالة داخلية صادرة\nمن1111\nبـSAR 500\nلـ3344; JOHN-PAUL O'NEIL JR. 3RD\n26/9/18 09:35"))!!
        assertEquals("JOHN-PAUL O'NEIL JR. 3RD", out.label)
        val old = transferPartyOf(sms("حوالة داخلية صادرة\nمن:1111\nمبلغ:SAR 500\nالى:سارة عبدالعزيز بن سعيد الدوسري\nالى:3344\nفي:26/9/18 09:35"))!!
        assertEquals("سارة عبدالعزيز بن سعيد الدوسري" to "3344", old.label to old.last4)
        val dotted = transferPartyOf(sms("حوالة داخلية واردة\nمبلغ:SAR 500\nالى:9010\nمن:عبدالرحمن م. العتيبي 2\nفي:26/9/18 09:35"))!!
        assertEquals("عبدالرحمن م. العتيبي 2", dotted.label, "النقطة والرقم جوه الاسم ما بيقطعوهوش")
    }

    @Test fun egyptWalletInitialDotNoLongerCutsTheNameButKeepsItsKey() {
        val party = transferPartyOf(sms("تم استلام مبلغ 500.00 جنيه من رقم 01012345678 المسجل بإسم محمد أ. السيد على رقم 01198765432 في 18-09-2026", egypt = true))!!
        assertEquals("محمد أ. السيد", party.label)
        assertEquals("محمدا#5678", party.key, "المفتاح زي ما كان — القرار المحفوظ على الطرف ده لسه شغال")
        // من غير «على رقم» بعده ⇒ ما بنمدّش (ممكن ياخد جملة تانية)
        val noTail = transferPartyOf(sms("تم استلام مبلغ 500.00 جنيه من رقم 01012345678 المسجل بإسم محمد أ. في 18-09-2026", egypt = true))
        assertEquals("محمد أ", noTail?.label)
    }

    @Test fun alrajhiStatementNamesWithDashOrDotAreWholeWithTheSameKey() {
        val dash = transferPartyOf(statement(internalOp, "عبدالله آل-سالمW-/FRACCT/12345678901234567FR:ملاحظة"))!!
        assertEquals(TransferPartyRef("سالم#4567", "عبدالله آل-سالم", "4567"), dash)
        val dot = transferPartyOf(statement(internalOp, "عبدالله م. سالمW-/FRACCT/12345678901234567FR:ملاحظة"))!!
        assertEquals("عبدالله م. سالم" to "سالم#4567", dot.label to dot.key)
        // أطول من 40 حرف قبل الحساب: كان بيضيع أوله
        val long = transferPartyOf(statement(internalOp, "عبدالرحمن بن عبدالعزيز بن محمد آل سعود الكبيرW-/FRACCT/12345678901234567FR:ملاحظة"))!!
        assertEquals("عبدالرحمن بن عبدالعزيز بن محمد آل سعود الكبير", long.label)
        // الرقم أو «:» قبل الاسم مش جزء منه
        val after = transferPartyOf(statement(internalOp, "ملاحظة:12 سامي-علي W-/TOACCT/12345678901234567TO:x", Direction.OUT))!!
        assertEquals("سامي-علي", after.label)
    }

    /** كشف الراجحي القديم بحروف العرض المقلوبة: «آل-سالم» كانت بتتقلب حتة حتة فبيطلع «عبدالله آل» بس (أول الاسم). */
    @Test fun visualOrderNameWithADashIsTurnedAsOneSentence() {
        val visual = "ﻢﻟﺎﺳ-ﻝﺁ ﷲﺪﺒﻋW-/FRACCT/12345678901234567FR:ملاحظة"
        val party = transferPartyOf(statement(internalOp, visual))!!
        assertEquals("عبدالله آل-سالم", party.label)
        assertEquals("عبداللهال#4567", party.key, "المفتاح من القراية القديمة زي ما هو")
        assertEquals("عبدالله آل-سالم", nfkc(logicalArabicJoined("ﻢﻟﺎﺳ-ﻝﺁ ﷲﺪﺒﻋ")))
    }

    @Test fun namesTheBankCutAreMarked() {
        val masked = transferPartyOf(sms("حوالة محلية صادرة\nمصرف:ANB\nمن:1111\nمبلغ:SAR 500\nالى:عبدالله محم****\nالى:3344\nالرسوم:SAR 0.58\nفي:26/9/18 09:35"))!!
        assertEquals("عبدالله محم****" to NameCut.SMS, masked.label to masked.cut)
        val qnb = transferPartyOf(statement("IPN TRANSFER", "IPN TRANSFER-VC••••1234-IPNMOHAMED_ABDEc1d2e3f4a5b6ACC -2026"))!!
        assertEquals("MOHAMED ABDE" to NameCut.STATEMENT, qnb.label to qnb.cut, "اسم QNB مالي خانته (12) ⇒ ممكن البنك قصّه")
        val short = transferPartyOf(statement("IPN TRANSFER", "IPN TRANSFER-VC••••1234-IPNTEST_PERSONa1b2c3d4e5f6ACC -2026"))!!
        assertNull(short.cut, "11 حرف ⇒ الاسم كامل")
        assertTrue(looksCutByBank("سامي عبدالـ"))
        assertTrue(looksCutByBank("SAMI AL…"))
        assertTrue(looksCutByBank("SAMI AL.."))
        assertFalse(looksCutByBank("سامي م. علي"), "الاختصار بنقطة مش قص")
        assertFalse(looksCutByBank("••••4821".drop(4)), "الأرقام بس مش اسم مقصوص")
        assertNull(transferPartyOf(sms("حوالة داخلية صادرة\nالرصيد: 1500 SAR\nمبلغ:SAR 35.62\nإلى: 9999\n26/9/18 09:35"))!!.cut, "حساب بس ⇒ مفيش اسم يتقص")
    }

    @Test fun personNameDropsTheCutMarks() {
        assertEquals("عبدالله محم", personNameFrom("عبدالله محم****"))
        assertEquals("سامي عبدال", personNameFrom("سامي عبدالـ"))
        assertEquals("SAMI AL", personNameFrom("SAMI AL…"))
        assertEquals("سامي علي", personNameFrom("سامي علي"))
        assertEquals("••••", personNameFrom("••••"), "مفيش اسم خالص ⇒ زي ما هو")
    }

    @Test fun fullerLabelOnlyExtendsNeverReplaces() {
        assertEquals("عبدالله آل-سالم", fullerLabel("سالم", "عبدالله آل-سالم"))
        assertEquals("سالم", fullerLabel("سالم", "شركة مثال"), "اسم تاني ⇒ القديم")
        assertEquals("سالم", fullerLabel("سالم", null))
        assertEquals(PARTY_LABEL_MAX, fullerLabel("أ", "أ".repeat(200)).length)
    }

    @Test fun veryLongNamesShowWholeUpToTheLimitWithTheOldKey() {
        val name = "عبدالرحمن بن عبدالعزيز بن محمد بن عبدالله بن سعود بن فيصل آل سعود الكبير"
        val party = transferPartyOf(sms("حوالة داخلية واردة\nبـSR 500\nلـ9010\nمن2134;$name\n26/9/18 09:35"))!!
        assertEquals(name, party.label)
        assertEquals(normalizeCompact(name.take(60)) + "#2134", party.key, "المفتاح من أول 60 زي ما كان")
    }

    @Test fun suggestedPersonNameDropsCutMarksOnlyWhenTheBankCutTheName() {
        assertEquals("عبدالله محم", suggestedPersonName(TransferPartyRef("k1", "عبدالله محم****", null, NameCut.SMS)))
        assertEquals("JOHN-PAUL O'NEIL JR.", suggestedPersonName(TransferPartyRef("k2", "JOHN-PAUL O'NEIL JR.", null)), "مش مقصوص ⇒ النقطة بتفضل")
        assertEquals("م. سالم-2 الحربي", suggestedPersonName(TransferPartyRef("k3", "م. سالم-2 الحربي", "1234")))
    }
}
