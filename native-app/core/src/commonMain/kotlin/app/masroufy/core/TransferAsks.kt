package app.masroufy.core

/**
 * الأسئلة على تحويل مع **شخص مربوط** في «زون التحويلات» (قرارات المالك 2026-10-08 — §75-5 و§75-9، بتغيّر «الصادر = دعم» في §60):
 * - الصادر لشخص ⇒ «سلفة ولا دعم؟» **كل مرة** ([AskKind.LOAN_OR_SUPPORT]).
 * - الوارد من شخص **ليك عنده** دين مفتوح ⇒ «ده سداد السلفة؟»، والصادر لشخص **ليه عندك** قرض مفتوح ⇒ «ده سداد دين عليك؟»
 *   ([AskKind.DEBT_REPAYMENT]) — بدل ما يتحسب «تحصيل دين» لوحده.
 * - النوع اللي المالك أكده ⇒ مفيش سؤال. والوارد من غير دين مفتوح ما بيتسألش **هنا**: بيفضل يتسأل عن نوعه زي §39.1
 *   ([INCOMING_FROM_PERSON_KINDS]).
 * منطق صافي: المتبقي من `remainingOfObligation`، والفلوس بالهللة. الدين بيتحسب **بعملة العملية بس** (ريال ما بيسددش جنيه).
 */

/** دين مفتوح مع الشخص — [remainingMinor] أكبر من صفر. */
data class OpenDebt(val obligation: Obligation, val remainingMinor: Halalas)

/** الدين اللي التحويل ده ممكن يسدده: الوارد ⇒ «لك عنده» · الصادر ⇒ «قرض عليك» (الأمانة ليها نوعها لوحده — ما بتتسألش هنا). */
fun repayableKindOf(direction: Direction): ObligationKind =
    if (direction == Direction.IN) ObligationKind.RECEIVABLE else ObligationKind.LOAN_PAYABLE

/**
 * الديون المفتوحة اللي [t] ممكن يسددها مع [personId]: نفس النوع حسب الاتجاه ونفس العملة، **بالأقدم الأول** — الدين القديم من غير
 * عملية (§27) الأول، وبعدها بتاريخ عملية الدين ([originDates]: معرّف العملية ⇒ تاريخها)، وبعدها بالمعرّف عشان الترتيب يبقى ثابت.
 * تسويات العملية نفسها **ما بتتحسبش**: لو الإجابة اتقطعت في النص وبتتعاد، الخطة بتطلع هي هي (والتسوية بنفس المعرّف بترجع زي ما هي).
 */
fun openDebtsFor(
    t: Transaction,
    personId: Id,
    obligations: List<Obligation>,
    settlements: List<Settlement>,
    originDates: Map<Id, IsoDate> = emptyMap(),
): List<OpenDebt> {
    val kind = repayableKindOf(t.observedDirection)
    val others = settlements.filter { it.transactionId != t.id }
    return obligations
        .filter { it.personId == personId && it.kind == kind && it.currency == t.currency }
        .map { OpenDebt(it, remainingOfObligation(it, others)) }
        .filter { it.remainingMinor > 0 }
        .sortedWith(compareBy({ it.obligation.originTransactionId?.let { id -> originDates[id] }.orEmpty() }, { it.obligation.id }))
}

/**
 * السؤال على [t] لو طرفها [party] اتقرر إنه «شخص» — أو null. [debtRuledOut] = المالك قال «لأ، مش سداد» دلوقتي ⇒ الصادر بيرجع
 * «سلفة ولا دعم؟» والوارد مالوش سؤال هنا (اختيارات §39.1).
 */
fun transferAskOf(
    t: Transaction,
    party: TransferParty?,
    obligations: List<Obligation>,
    settlements: List<Settlement>,
    debtRuledOut: Boolean = false,
): AskKind? {
    val personId = party?.personId?.takeIf { party.verdict == TransferVerdict.PERSON } ?: return null
    if (t.economicKindConfirmed) return null
    if (!debtRuledOut && openDebtsFor(t, personId, obligations, settlements).isNotEmpty()) return AskKind.DEBT_REPAYMENT
    return if (t.observedDirection == Direction.OUT) AskKind.LOAN_OR_SUPPORT else null
}

/** خطة «أيوه، ده سداد»: التسويات بالترتيب، أو الرفض بسببه. */
sealed class RepaymentPlan {
    data class Settle(val parts: List<Pair<Obligation, Halalas>>) : RepaymentPlan()

    data class Refused(val reason: String) : RepaymentPlan()
}

/**
 * المبلغ على الديون المفتوحة **بالأقدم الأول** (سداد 500 على سلفتين 200 و300 بيقفلهم الاتنين). أكبر من المفتوح كله ⇒ مرفوض
 * **بنفس رسالة `checkSettlement`** (الزيادة ما بتتبلعش ولا بتتسحب من دين تاني — spec/06)، ومع دين واحد الرسالة هي هي حرفيًا.
 */
fun planRepayment(amountMinor: Halalas, debts: List<OpenDebt>): RepaymentPlan {
    val first = debts.firstOrNull() ?: return RepaymentPlan.Refused(uiText(TextKey.SETTLEMENT_FULLY_PAID))
    val open = sumMoney(debts.map { it.remainingMinor })
    // الفحص على المفتوح كله كأنه دين واحد: نفس النوع والعملة، والمتبقي = المجموع
    val check = checkSettlement(first.obligation.copy(originalMinor = open), emptyList(), amountMinor)
    if (!check.allowed) return RepaymentPlan.Refused(check.reason ?: uiText(TextKey.SETTLEMENT_REJECTED))
    var left = amountMinor
    val parts = mutableListOf<Pair<Obligation, Halalas>>()
    for (debt in debts) {
        if (left == 0L) break
        val part = minOf(left, debt.remainingMinor)
        parts += debt.obligation to part
        left = subtractMoney(left, part)
    }
    return RepaymentPlan.Settle(parts)
}
