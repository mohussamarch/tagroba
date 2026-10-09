package app.masroufy.core

/**
 * الأسئلة على تحويل مع **شخص مربوط** في «زون التحويلات» (قرارات المالك 2026-10-08 — §75-5 و§75-9، بتغيّر «الصادر = دعم» في §60):
 * - الصادر لشخص ⇒ «سلفة ولا دعم؟» **كل مرة** ([AskKind.LOAN_OR_SUPPORT]).
 * - الوارد من شخص **ليك عنده** دين مفتوح ⇒ «ده سداد السلفة؟»، والصادر لشخص **ليه عندك** قرض مفتوح ⇒ «ده سداد دين عليك؟»
 *   ([AskKind.DEBT_REPAYMENT]) — بدل ما يتحسب «تحصيل دين» لوحده.
 * - النوع اللي المالك أكده ⇒ مفيش سؤال. والوارد من غير دين مفتوح ما بيتسألش **هنا**: بيفضل يتسأل عن نوعه زي §39.1
 *   ([INCOMING_FROM_PERSON_KINDS]).
 * - العملية اللي **مربوطة بالدفتر من برّه السؤال** (سداد اتسجل عليها من «اربطها بدين موجود» §30، أو «دفعت عنه»/دين/هدية
 *   اتعملوا منها في شاشة الأشخاص — في التطبيق القديم أو الجديد) ⇒ **اتجاوبت** ومفيش سؤال (وإلا الفلوس بتتسدد مرتين).
 * منطق صافي: المتبقي من `remainingOfObligation`، والفلوس بالهللة. الدين بيتحسب **بعملة العملية بس** (ريال ما بيسددش جنيه).
 */

/*
 * اللي الإجابة بتكتبه بمعرّفات **ثابتة من معرّف العملية** — عشان الإجابة اللي اتقطعت تتعاد من غير ما تتكرر (وحدة عمل فايربيز
 * مش ذرّية)، وعشان يتعرف بعدين إن السؤال هو اللي كتبه (مش المالك من شاشة الأشخاص). الأشكال متخزنة في مستندات التطبيق القديم
 * نفسها (`obligations` · `allocations` · `settlements`) ومعرّفها نص حر — التطبيق القديم والنسخة الشاملة بيقروها عادي.
 */

private val SAFE_TXN_ID = Regex("^[A-Za-z0-9_-]{1,90}$")

/**
 * معرّف الطلب الثابت لإجابة السؤال على [txnId]: `ask-<المعرّف>`، والمعرّف اللي فيه حروف مش مسموحة في معرّف الطلب (أو طويل) بياخد
 * بصمة ثابتة منه بدل ما الحروف تتبدل — عشان عمليتين مختلفتين عمرهم ما ياخدوا نفس المعرّف.
 */
fun transferAskRequestId(txnId: Id): String =
    if (SAFE_TXN_ID.matches(txnId)) "ask-$txnId" else "ask-h" + fingerprint64(txnId) + "-" + txnId.length

/** دين «سلفة» اللي إجابة السؤال عملته من [txnId] — و[transferAskAllocationId] نصيب الشخص اللي معاه. */
fun transferAskObligationId(txnId: Id): Id = "obl-" + transferAskRequestId(txnId)

fun transferAskAllocationId(txnId: Id): Id = "alloc-" + transferAskRequestId(txnId)

/** تسوية «أيوه، ده سداد» — `ManagePeople.settle` بيسمّيها `stl-<الدين>-<معرّف الطلب>`. */
fun isTransferAskSettlement(s: Settlement): Boolean =
    s.transactionId.isNotEmpty() && s.id == "stl-${s.obligationId}-${transferAskRequestId(s.transactionId)}"

/** FNV-1a 64 على حروف المعرّف — بصمة مش فلوس. */
private fun fingerprint64(text: String): String {
    var hash = -0x340d631b7bdddcdbL
    for (c in text) {
        hash = hash xor c.code.toLong()
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16)
}

/** روابط العملية في دفتر الأشخاص: نصيب الأشخاص منها، والتسويات اللي هي سدادها، والديون اللي اتعملت منها. */
data class TransactionLedgerLinks(
    val allocations: List<PersonAllocation> = emptyList(),
    val settlements: List<Settlement> = emptyList(),
    val originated: List<Obligation> = emptyList(),
)

/** [t] مربوطة بالدفتر **من برّه السؤال** ⇒ المالك جاوب عليها بنفسه. اللي السؤال كتبه (بمعرّفاته الثابتة) ما بيتحسبش. */
fun linkedOutsideTheAsk(t: Transaction, links: TransactionLedgerLinks): Boolean =
    links.allocations.any { it.transactionId == t.id && it.id != transferAskAllocationId(t.id) } ||
        links.settlements.any { it.transactionId == t.id && !isTransferAskSettlement(it) } ||
        links.originated.any { it.originTransactionId == t.id && it.id != transferAskObligationId(t.id) }

/** دين مفتوح مع الشخص — [remainingMinor] أكبر من صفر. */
data class OpenDebt(val obligation: Obligation, val remainingMinor: Halalas)

/** الدين اللي التحويل ده ممكن يسدده: الوارد ⇒ «لك عنده» · الصادر ⇒ «قرض عليك» (الأمانة ليها نوعها لوحده — ما بتتسألش هنا). */
fun repayableKindOf(direction: Direction): ObligationKind =
    if (direction == Direction.IN) ObligationKind.RECEIVABLE else ObligationKind.LOAN_PAYABLE

/**
 * الديون المفتوحة اللي [t] ممكن يسددها مع [personId]: نفس النوع حسب الاتجاه ونفس العملة، **بالأقدم الأول** — الدين القديم من غير
 * عملية (§27) الأول، وبعدها بتاريخ عملية الدين ([originDates]: معرّف العملية ⇒ تاريخها)، وبعدها بالمعرّف عشان الترتيب يبقى ثابت.
 * **الدين اللي اتعمل بعد [t] ما بيتحسبش**: فلوس جت في يناير ما بتسددش سلفة اتعملت في أكتوبر. الدين اللي ليه عملية بيتحسب بس لو
 * تاريخها يوم [t] أو قبله؛ الدين القديم من غير عملية (§27) — أو اللي تاريخ عمليته مش في [originDates] — بيتحسب دايمًا.
 * التسويات اللي **إجابة السؤال على [t] نفسها** كتبتها ما بتتحسبش: لو الإجابة اتقطعت في النص وبتتعاد، الخطة بتطلع هي هي (والتسوية
 * بنفس المعرّف بترجع زي ما هي). أي تسوية تانية (حتى على [t] من شاشة الأشخاص) بتتحسب.
 */
fun openDebtsFor(
    t: Transaction,
    personId: Id,
    obligations: List<Obligation>,
    settlements: List<Settlement>,
    originDates: Map<Id, IsoDate> = emptyMap(),
): List<OpenDebt> {
    val kind = repayableKindOf(t.observedDirection)
    val others = settlements.filterNot { it.transactionId == t.id && isTransferAskSettlement(it) }
    val day = t.occurredAt.take(10)
    return obligations
        .filter { it.personId == personId && it.kind == kind && it.currency == t.currency }
        .filter { o -> o.originTransactionId?.let { originDates[it] }?.let { it.take(10) <= day } ?: true }
        .map { OpenDebt(it, remainingOfObligation(it, others)) }
        .filter { it.remainingMinor > 0 }
        .sortedWith(compareBy({ it.obligation.originTransactionId?.let { id -> originDates[id] }.orEmpty() }, { it.obligation.id }))
}

/**
 * السؤال على [t] لو طرفها [party] اتقرر إنه «شخص» — أو null. [debtRuledOut] = المالك قال «لأ، مش سداد» دلوقتي ⇒ الصادر بيرجع
 * «سلفة ولا دعم؟» والوارد مالوش سؤال هنا (اختيارات §39.1). «لأ» **ما بتتخزنش**: الشاشة بتمررها في نفس الخطوة.
 * [links] = روابط [t] في الدفتر — مربوطة من برّه السؤال ([linkedOutsideTheAsk]) ⇒ null. تسوية من برّه السؤال على [t] في
 * [settlements] نفسها (ديون الشخص) بتتحسب ربط حتى لو [links] ما اتمررتش. [originDates] = تواريخ عمليات الديون ([openDebtsFor]).
 */
fun transferAskOf(
    t: Transaction,
    party: TransferParty?,
    obligations: List<Obligation>,
    settlements: List<Settlement>,
    debtRuledOut: Boolean = false,
    links: TransactionLedgerLinks = TransactionLedgerLinks(),
    originDates: Map<Id, IsoDate> = emptyMap(),
): AskKind? {
    val personId = party?.personId?.takeIf { party.verdict == TransferVerdict.PERSON } ?: return null
    if (t.economicKindConfirmed || linkedOutsideTheAsk(t, links.copy(settlements = links.settlements + settlements))) return null
    if (!debtRuledOut && openDebtsFor(t, personId, obligations, settlements, originDates).isNotEmpty()) return AskKind.DEBT_REPAYMENT
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
