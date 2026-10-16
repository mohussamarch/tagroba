package app.masroufy.ui.screens.more

import app.masroufy.core.UiKey
import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Language
import app.masroufy.core.MAX_NAME_LENGTH
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.emptyProfile
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «ملفك»: الإجابة المجهولة «لم تُجب بعد» (null) مش «لا»، والملف الجديد بيتبني من اختيار المستخدم بس. */
class AccountStateTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val blank = emptyProfile()

    private fun rowsOf(p: app.masroufy.core.UserProfile, currency: Currency = Currency.SAR) =
        accountGroups(p, currency).flatMap { it.rows }.associateBy { it.field }

    @Test
    fun unansweredQuestionsStayUnansweredNotNo() {
        val rows = rowsOf(blank)
        listOf(AccountField.NAME, AccountField.GENDER, AccountField.DEPENDENTS, AccountField.SALARY, AccountField.CAR, AccountField.RENTER, AccountField.MAID, AccountField.BUSINESS)
            .forEach { assertNull(rows.getValue(it).value, "$it اتعرض كأنه اتجاوب") }
        // يوم الراتب ليه قيمة افتراضية دايمًا
        assertEquals(t(UiKey.ACC_DAY_N, sentenceNumber(blank.payday)), rows.getValue(AccountField.PAYDAY).value)
    }

    @Test
    fun groupsFollowThePrototypeOrder() {
        val groups = accountGroups(blank, Currency.SAR)
        assertEquals(listOf(UiKey.ACC_GROUP_ABOUT, UiKey.ACC_GROUP_INCOME, UiKey.ACC_GROUP_LIFE), groups.map { it.title })
        assertEquals(UiKey.ACC_LIFE_NOTE, groups[2].note)
        assertEquals(UiKey.ACC_SALARY_HINT, groups[1].rows[0].hint)
    }

    @Test
    fun answersAreShownInWords() {
        val p = blank.copy(
            displayName = "محمد", gender = "male", supportsDependents = true, dependentKinds = listOf("spouse", "children"),
            salaryMinor = 1_250_000, payday = 27, hasCar = true, carToWork = false, renter = false, domesticWorker = true, business = null,
        )
        val rows = rowsOf(p)
        assertEquals("محمد", rows.getValue(AccountField.NAME).value)
        assertEquals(t(UiKey.ACC_GENDER_MALE), rows.getValue(AccountField.GENDER).value)
        assertEquals(t(UiKey.ACC_DEP_SPOUSE) + "، " + t(UiKey.ACC_DEP_CHILDREN), rows.getValue(AccountField.DEPENDENTS).value)
        assertEquals(amountLabel(1_250_000, Currency.SAR), rows.getValue(AccountField.SALARY).value)
        assertEquals(t(UiKey.ACC_CAR_WITH_WORK, t(UiKey.MORE_YES), t(UiKey.ACC_CAR_NOT_WORK)), rows.getValue(AccountField.CAR).value)
        assertEquals(t(UiKey.MORE_NO), rows.getValue(AccountField.RENTER).value)
        assertEquals(t(UiKey.MORE_YES), rows.getValue(AccountField.MAID).value)
        assertNull(rows.getValue(AccountField.BUSINESS).value)
    }

    @Test
    fun dependentsNoneAndYesWithoutKinds() {
        assertEquals(t(UiKey.ACC_DEP_NONE), dependentsValue(blank.copy(supportsDependents = false)))
        assertEquals(t(UiKey.MORE_YES), dependentsValue(blank.copy(supportsDependents = true, dependentKinds = emptyList())))
        assertNull(dependentsValue(blank))
    }

    @Test
    fun carWithoutTheWorkAnswerIsJustYes() {
        assertEquals(t(UiKey.MORE_YES), carValue(blank.copy(hasCar = true)))
        assertEquals(t(UiKey.MORE_NO), carValue(blank.copy(hasCar = false, carToWork = true)))
        assertNull(carValue(blank))
    }

    @Test
    fun twoJobsShowTheTotalWithTheJobCountAndAFieldEach() {
        fun job(id: String, expected: Long?) = app.masroufy.core.IncomeSource(
            id, "جهة وهمية $id", "جهه وهميه $id", app.masroufy.core.IncomeSourceKind.JOB, Currency.SAR, "2025-01-01", expectedMinor = expected,
        )
        val total = app.masroufy.core.SalaryState(1_150_050, Currency.SAR, declined = false, wasRange = false, employers = listOf(job("a", 900_000), job("b", 250_050)))
        assertEquals(amountLabel(1_150_050, Currency.SAR), salaryValue(blank, Currency.SAR, total))
        assertEquals(UiKey.ACC_SALARY_TOTAL_OF, salaryHint(total))
        assertEquals(sentenceNumber(2), salaryHintArg(total))
        val row = accountGroups(blank, Currency.SAR, total)[1].rows[0]
        assertEquals(t(UiKey.ACC_SALARY_TOTAL_OF, sentenceNumber(2)), t(row.hint!!, row.hintArg!!))
        assertEquals(listOf("a", "b"), salaryKeys(total))
        val draft = salaryDraft(total)
        assertEquals(mapOf("a" to 900_000L, "b" to 250_050L), readSalaryForm(draft, total, Currency.SAR))
        assertNull(readSalaryForm(draft + ("b" to ""), total, Currency.SAR), "خانة فاضية ⇒ «احفظ» مقفول")
        val missing = total.copy(amountMinor = null, employers = listOf(job("a", 900_000), job("b", null)))
        assertNull(salaryValue(blank, Currency.SAR, missing), "جهة من غير مبلغ ⇒ «لم تُجب بعد»")
        assertEquals(UiKey.ACC_SALARY_EACH_JOB, salaryHint(missing))
        assertNull(salaryHintArg(missing))
    }

    @Test
    fun retirementShowsWhereTheSalaryCameFrom() {
        fun d(salary: Long?, from: app.masroufy.usecase.RetirementSalaryFrom) =
            app.masroufy.usecase.RetirementDefaults(salary, salary, null, null, null, 0, salaryFrom = from)
        val profile = app.masroufy.ui.screens.investment.calc.retirementSalaryLine(d(800_000, app.masroufy.usecase.RetirementSalaryFrom.PROFILE), Currency.SAR)
        assertEquals(t(UiKey.RETCALC_SALARY_FROM_PROFILE, amountLabel(800_000, Currency.SAR)), profile)
        val job = app.masroufy.ui.screens.investment.calc.retirementSalaryLine(d(900_000, app.masroufy.usecase.RetirementSalaryFrom.EMPLOYER), Currency.SAR)
        assertEquals(t(UiKey.RETCALC_SALARY_FROM_JOB, amountLabel(900_000, Currency.SAR)), job)
        assertEquals(t(UiKey.RETCALC_SALARY_NONE), app.masroufy.ui.screens.investment.calc.retirementSalaryLine(d(null, app.masroufy.usecase.RetirementSalaryFrom.NONE), Currency.SAR))
        assertNull(app.masroufy.ui.screens.investment.calc.retirementSalaryLine(null, Currency.SAR), "لسه بيتحمّل")
    }

    @Test
    fun salaryIsTheExactTypedAmountAndAnOldRangeAsksToTypeIt() {
        val sar = app.masroufy.core.SalaryState(1_250_050, Currency.SAR, declined = false, wasRange = false, employers = emptyList())
        assertEquals(amountLabel(1_250_050, Currency.SAR), salaryValue(blank, Currency.SAR, sar))
        val skip = sar.copy(amountMinor = null, declined = true)
        assertEquals(t(UiKey.ACC_SAL_SKIP), salaryValue(blank, Currency.EGP, skip))
        val range = sar.copy(amountMinor = null, wasRange = true)
        assertNull(salaryValue(blank, Currency.SAR, range), "النطاق القديم مش رقم ⇒ «لم تُجب بعد»")
        assertEquals(UiKey.ACC_SALARY_WAS_RANGE, salaryHint(range))
        assertEquals(UiKey.ACC_SALARY_HINT, salaryHint(sar))
        assertNull(salaryValue(blank, Currency.SAR, null))
        assertNull(salaryValue(blank.copy(salaryMinor = 500_000, salaryCurrency = Currency.SAR), Currency.EGP, null), "ريال ما يتعرضش بالجنيه")
        assertEquals(1_250_050L, readSalaryText(salaryDraft(sar).getValue(app.masroufy.core.PROFILE_SALARY_KEY), Currency.SAR).minor, "المسودة بتتقري هي هي")
        assertEquals(SalaryText(1_250_050, null), readSalaryText(" 12500.50 ", Currency.SAR))
        assertEquals(SalaryText(850_050, null), readSalaryText("٨٥٠٠٫٥٠", Currency.SAR), "الأرقام العربية مقبولة")
        assertEquals(SalaryText(null, UiKey.ACC_SALARY_BAD), readSalaryText("0", Currency.SAR))
        assertEquals(SalaryText(null, UiKey.ACC_SALARY_NOT_NUMBER), readSalaryText("ثمانية آلاف", Currency.SAR))
        assertEquals(SalaryText(null, UiKey.ACC_SALARY_NOT_NUMBER), readSalaryText("8500.505", Currency.SAR), "خانتين عشريتين بالكتير")
        assertEquals(SalaryText(null, null), readSalaryText("  ", Currency.SAR))
    }

    @Test
    fun applyAnswerBuildsTheNewProfile() {
        assertEquals("سارة", applyAnswer(blank, AccountField.NAME, "  سارة ")!!.displayName)
        assertNull(applyAnswer(blank.copy(displayName = "س"), AccountField.NAME, "   ")!!.displayName)
        assertEquals("female", applyAnswer(blank, AccountField.GENDER, "female")!!.gender)

        val deps = applyAnswer(blank, AccountField.DEPENDENTS, listOf("parents", "spouse"))!!
        assertEquals(listOf("spouse", "parents"), deps.dependentKinds, "الترتيب ثابت مهما اتختار")
        assertEquals(true, deps.supportsDependents)
        val none = applyAnswer(blank, AccountField.DEPENDENTS, emptyList<String>())!!
        assertEquals(false, none.supportsDependents)
        assertNull(applyAnswer(blank, AccountField.DEPENDENTS, null), "مفيش إجابة ⇒ الحفظ مقفول")

        assertEquals(1, applyAnswer(blank, AccountField.PAYDAY, 1)!!.payday)
        assertEquals(true, applyAnswer(blank, AccountField.CAR, true)!!.hasCar)
        assertEquals(false, applyAnswer(blank, AccountField.RENTER, false)!!.renter)
        assertEquals(true, applyAnswer(blank, AccountField.MAID, true)!!.domesticWorker)
        assertEquals(false, applyAnswer(blank, AccountField.BUSINESS, false)!!.business)
        assertNull(applyAnswer(blank, AccountField.CAR, null))
        assertNull(applyAnswer(blank, AccountField.SALARY, 5), "الراتب نقطة ربط (النطاقات §63) — مش بيتكتب من هنا")
    }

    @Test
    fun duesInBudgetDefaultsToCountedUntilAsked() {
        assertTrue(duesOn(blank))
        assertEquals(UiKey.ACC_DUES_UNSET, duesDescription(blank))
        assertEquals(UiKey.ACC_DUES_ON, duesDescription(blank.copy(duesInBudget = true)))
        assertFalse(duesOn(blank.copy(duesInBudget = false)))
        assertEquals(UiKey.ACC_DUES_OFF, duesDescription(blank.copy(duesInBudget = false)))
    }

    @Test
    fun nameLimitMatchesTheProfileCheck() {
        assertFalse(nameTooLong("a".repeat(MAX_NAME_LENGTH) + "  "))
        assertTrue(nameTooLong("a".repeat(MAX_NAME_LENGTH + 1)))
    }

    @Test
    fun egyptianWordingForAnswers() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        val rows = rowsOf(blank.copy(renter = true, supportsDependents = false), Currency.EGP)
        assertEquals("أيوه", rows.getValue(AccountField.RENTER).value)
        assertEquals("محدش", rows.getValue(AccountField.DEPENDENTS).value)
        Texts.arabicVariant = ArabicVariant.MSA
        val msa = rowsOf(blank.copy(renter = true, supportsDependents = false))
        assertEquals("نعم", msa.getValue(AccountField.RENTER).value)
        assertEquals("لا أحد", msa.getValue(AccountField.DEPENDENTS).value)
    }
}
