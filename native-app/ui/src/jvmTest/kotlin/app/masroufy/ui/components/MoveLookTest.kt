package app.masroufy.ui.components

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Language
import app.masroufy.core.Texts
import app.masroufy.core.Transaction
import app.masroufy.ui.icons.vector
import app.masroufy.ui.screens.operations.Fx
import app.masroufy.ui.screens.operations.RowContext
import app.masroufy.ui.screens.operations.detailView
import app.masroufy.ui.screens.operations.opRow
import app.masroufy.ui.theme.Ink
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * شكل التحويل بين محافظك (طلب المالك HANDOVER §6.5-10 — «سالب أحمر» ⇒ محايد ورمز «منه فيه»): من النوع للون والرمز والعلامة والسطر،
 * للتحويل بين محافظك (طالع · داخل · رجل لوحدها) والتحويل لشخص والصرف والدخل — بالسعودية (فصحى · ريال) ومصر (مصري · جنيه) والإنجليزي،
 * وترتيب «من ← إلى» من اليمين للشمال. بيانات وهمية (المستودع عام).
 */
class MoveLookTest {
    @AfterTest fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val ctx = RowContext(listOf(Fx.food), wallets = listOf(Fx.bank, Fx.cash))
    private fun name(id: String?): String? = listOf(Fx.bank, Fx.cash).firstOrNull { it.id == id }?.name

    /** بين محفظتين (عملية واحدة ليها محفظة مستلمة — «+» · المساعد · السحب للكاش). */
    private val moveOut = Fx.tx("mv", amount = 150_000, kind = EconomicKind.INTERNAL_TRANSFER, category = null, merchant = null, toWallet = Fx.cash.id)

    /** رجل لوحدها من رسالة البنك («حسابي التاني» §60 · آخر 4 أرقام §75-11) — الطرف التاني مش محفظة هنا. */
    private val legOut = Fx.tx("leg-out", amount = 70_000, kind = EconomicKind.INTERNAL_TRANSFER, category = null, merchant = "حوالة بين حساباتك")
    private val legIn = Fx.tx("leg-in", amount = 70_000, direction = Direction.IN, kind = EconomicKind.INTERNAL_TRANSFER, category = null, merchant = "حوالة واردة")

    /** تحويل لشخص (مش حسابك) — لسه نوعه ما اتحددش، ودعم مؤكد. */
    private val toPerson = Fx.tx("p", amount = 30_000, kind = EconomicKind.UNCLASSIFIED, confirmedKind = false, category = null, merchant = null,
        description = "حوالة محلية صادرة\nإلى:TEST PERSON\nبـSR 300\nمن:1111\n26/9/18")
    private val gift = Fx.tx("g", amount = 20_000, kind = EconomicKind.SUPPORT_GIFT, category = null, merchant = "TEST PERSON")
    private val expense = Fx.tx("e", amount = 4_200)
    private val income = Fx.tx("i", amount = 900_000, direction = Direction.IN, kind = EconomicKind.SALARY, category = null, merchant = null)

    @Test fun ownMovesAreNeutralWithTheSwapSymbolAndNoSign() {
        for (tx in listOf(moveOut, legOut, legIn)) {
            assertTrue(isOwnMove(tx), tx.id)
            val row = opRow(tx, ctx)
            assertEquals(AmountTone.TRANSFER, row.tone, "${tx.id}: محايد")
            assertEquals(Ink.transfer, row.tone.color)
            assertNotEquals(Ink.expense, row.tone.color, "مش الأحمر")
            assertNotEquals(Ink.income, row.tone.color, "مش الأخضر")
            assertEquals(MOVE_SWAP, row.icon, "${tx.id}: رمز «منه فيه»")
            assertEquals(Ink.transfer, row.color)
            assertEquals(tx.amountMinor, row.amountMinor, "المبلغ زي ما هو بالهللة")
            val label = amountLabel(row.amountMinor, row.currency, row.tone)
            assertFalse(label.contains("−") || label.contains("-") || label.contains("+"), "${tx.id}: من غير علامة — $label")
        }
        assertEquals("1,500.00 ر.س", amountLabel(moveOut.amountMinor, Currency.SAR, AmountTone.TRANSFER))
    }

    @Test fun ownMoveLinesSayFromAndToInBothArabicVariantsAndEnglish() {
        assertEquals("من حساب الراتب ← إلى الكاش", opRow(moveOut, ctx).subtitle)
        assertEquals("من حساب الراتب ← إلى حسابك الآخر", opRow(legOut, ctx).subtitle, "الرجل الطالعة: الطرف التاني مش محفظة هنا")
        assertEquals("من حسابك الآخر ← إلى حساب الراتب", opRow(legIn, ctx).subtitle, "الرجل الداخلة")
        assertEquals("اليوم، من حساب الراتب ← إلى الكاش", detail(moveOut).sub, "التفاصيل: «من ← إلى» مكان «مدين»")
        assertEquals(AmountTone.TRANSFER, detail(moveOut).tone)

        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("من حساب الراتب ← لـالكاش", opRow(moveOut, ctx).subtitle, "المصري بـ«لـ» زي «من {0} لـ{1}» في جداوله")
        assertEquals("من حساب الراتب ← لـحسابك التاني", opRow(legOut, ctx).subtitle)
        assertEquals("النهارده، من حساب الراتب ← لـالكاش", detail(moveOut).sub)

        Texts.language = Language.EN
        assertEquals("From حساب الراتب → الكاش", opRow(moveOut, ctx).subtitle)
        assertEquals("From your other account → حساب الراتب", opRow(legIn, ctx).subtitle)
    }

    @Test fun arabicLabelReadsRightToLeftFromThenArrowThenTo() {
        for (variant in ArabicVariant.entries) {
            Texts.arabicVariant = variant
            val line = moveLineOf(moveOut, ::name)!!
            val from = line.indexOf("حساب الراتب")
            val arrow = line.indexOf("←")
            val to = line.indexOf("الكاش")
            assertTrue(line.startsWith("من "), "$variant: «من» الأول — $line")
            assertTrue(from in 0 until arrow && arrow < to, "$variant: «من X ← إلى Y» بالترتيب المنطقي (العرض من اليمين) — $line")
            assertFalse(line.contains("→"), "العربي سهمه «←» في اتجاه القراية")
        }
        Texts.language = Language.EN
        val en = moveLineOf(moveOut, ::name)!!
        assertTrue(en.indexOf("حساب الراتب") < en.indexOf("→") && en.indexOf("→") < en.indexOf("الكاش"), en)
        assertFalse(en.contains("←"))
    }

    @Test fun insideOneWalletTheDirectionIsAWordNotAColour() {
        assertEquals("إلى الكاش", moveSideLineOf(moveOut, Fx.bank.id, ::name), "في سجل البنك: طالعة")
        assertEquals("من حساب الراتب", moveSideLineOf(moveOut, Fx.cash.id, ::name), "في سجل الكاش: داخلة")
        assertEquals("إلى حسابك الآخر", moveSideLineOf(legOut, Fx.bank.id, ::name))
        assertEquals("من حسابك الآخر", moveSideLineOf(legIn, Fx.bank.id, ::name))
        assertNull(moveSideLineOf(expense, Fx.bank.id, ::name), "الصرف مالوش «من/إلى»")
        assertEquals("9 أكتوبر، إلى الكاش", withMoveNote("9 أكتوبر", "إلى الكاش"))
        assertEquals("9 أكتوبر", withMoveNote("9 أكتوبر", null))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("لـالكاش", moveSideLineOf(moveOut, Fx.bank.id, ::name))
        assertEquals("من حسابك التاني", moveSideLineOf(legIn, Fx.bank.id, ::name))
        Texts.language = Language.EN
        assertEquals("To الكاش", moveSideLineOf(moveOut, Fx.bank.id, ::name))
        assertEquals("9 Oct, To الكاش", withMoveNote("9 Oct", "To الكاش"))
    }

    @Test fun transfersToOtherPeopleExpensesAndIncomeKeepTheirLook() {
        for (tx in listOf(toPerson, gift)) {
            assertFalse(isOwnMove(tx), "${tx.id}: التحويل لشخص مش بين محافظك")
            val row = opRow(tx, ctx)
            assertEquals(AmountTone.EXPENSE, row.tone, "${tx.id}: أحمر زي ما هو")
            assertNotEquals(MOVE_SWAP, row.icon)
            assertFalse(row.subtitle.contains("←"), row.subtitle)
            assertTrue(amountLabel(row.amountMinor, row.currency, row.tone).startsWith("−"), "علامة الطرح الحقيقية")
        }
        val e = opRow(expense, ctx)
        assertEquals(AmountTone.EXPENSE, e.tone)
        assertEquals("مطاعم وقهوة", e.subtitle)
        assertEquals("−42.00 ر.س", amountLabel(e.amountMinor, e.currency, e.tone))
        val i = opRow(income, ctx)
        assertEquals(AmountTone.INCOME, i.tone)
        assertEquals(Ink.income, i.tone.color)
        assertEquals("+9,000.00 ر.س", amountLabel(i.amountMinor, i.currency, i.tone))
        assertNull(moveLineOf(expense, ::name))
        assertNull(moveLineOf(income, ::name))
    }

    @Test fun egyptAndThePoundLookTheSame() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        val egp = moveOut.copy(currency = Currency.EGP)
        val row = opRow(egp, ctx)
        assertEquals(AmountTone.TRANSFER, row.tone)
        assertEquals(Currency.EGP, row.currency)
        assertEquals("1,500.00 ج.م", amountLabel(row.amountMinor, row.currency, row.tone), "الجنيه من غير علامة")
        assertEquals("−42.00 ج.م", amountLabel(4_200, Currency.EGP, opRow(expense.copy(currency = Currency.EGP), ctx).tone))
        assertEquals("+9,000.00 ج.م", amountLabel(900_000, Currency.EGP, opRow(income.copy(currency = Currency.EGP), ctx).tone))
    }

    @Test fun aCancelledPairStaysNeutralButIsNotCalledAMove() {
        val pair = legOut.copy(id = "orig", reversedById = "ret")
        assertEquals(AmountTone.TRANSFER, moneyToneOf(pair), "زي ما كانت: محايدة")
        assertNull(moveLineOf(pair, ::name), "عملية ورجوعها مش نقل بين محفظتين")
        assertEquals("تحويل داخلي", opRow(pair, ctx).subtitle, "سطرها اسم النوع زي الأول")
        assertNull(moveSideLineOf(pair, Fx.bank.id, ::name))
    }

    @Test fun transferZoneAccountThatIsYoursIsNeutralWithWords() {
        assertEquals(AmountTone.TRANSFER, partyAmountTone(ownAccount = true, incoming = true))
        assertEquals(AmountTone.TRANSFER, partyAmountTone(ownAccount = true, incoming = false))
        assertEquals("وارد منه", partyAmountLabel(ownAccount = true, incoming = true))
        assertEquals("صادر إليه", partyAmountLabel(ownAccount = true, incoming = false))
        assertEquals(AmountTone.INCOME, partyAmountTone(ownAccount = false, incoming = true), "شخص: زي ما هو")
        assertEquals(AmountTone.EXPENSE, partyAmountTone(ownAccount = false, incoming = false))
        assertNull(partyAmountLabel(ownAccount = false, incoming = true))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("جاي منه", partyAmountLabel(ownAccount = true, incoming = true))
        assertEquals("رايح له", partyAmountLabel(ownAccount = true, incoming = false))
    }

    @Test fun theSwapSymbolIsAValidLucideStyleVector() {
        assertEquals("MOVE_SWAP", MOVE_SWAP.name)
        val v = MOVE_SWAP.vector
        assertEquals(24f, v.viewportWidth)
        assertEquals(24f, v.viewportHeight)
    }

    private fun detail(tx: Transaction) = detailView(tx, listOf(Fx.food), listOf(Fx.bank, Fx.cash), emptyList(), emptySet(), Fx.TODAY)
}
