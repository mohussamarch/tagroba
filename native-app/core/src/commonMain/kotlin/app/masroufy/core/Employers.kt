package app.masroufy.core

/**
 * «جهات الشغل» (قايمة المالك 2026-10-15، البندين 6 و7 — HANDOVER §6.5): جهة الشغل = **مصدر دخل بمرتب** ([SALARIED_KINDS]) من نفس
 * مجموعة `incomeSources` — مفيش كيان جديد. ليها فترة (من ـ لحد، والفاضي = لسه شغال) و**أكتر من حساب مُحوِّل** ([IncomeSource.payerKeys] —
 * مفتاح الطرف = الاسم المضغوط + آخر 4 بس، قاعدة 11). المالك اشتغل في جهة لحد آخر أغسطس وبعدها في جهة تانية، والقديمة كانت بتحوّل من
 * حسابين ⇒ أي تحويل من **أي** حساب من حسابات الجهة **جوه فترتها** = راتب الجهة دي. برّه الفترة ⇒ ما بيتعلّمش لوحده (بيفضل يتسأل).
 * دوال نقية — الحفظ في `ManageEmployers` و`EmployerRetroApply` و`IncomeAnswers`.
 */

/** حساب مُحوِّل لجهة شغل: الاسم زي ما ورد في الرسالة أو الكشف + آخر 4 أرقام بس (CLAUDE.md #11). */
data class PayerAccount(val key: String, val label: String, val last4: String?)

/** آخر 4 أرقام من مفتاح الطرف («…#1234») — null لو المفتاح من غير أرقام. */
fun last4OfPartyKey(key: String): String? =
    key.substringAfterLast('#', "").takeIf { it.length == 4 && it.all { c -> c in '0'..'9' } }

/**
 * حسابات الجهة للعرض: الاسم من آخر عملية جت من الحساب ده ([seen] — من [incomingSenders])، ولو مفيش عملية ⇒ «•••• آخر 4» أو المفتاح.
 * بالترتيب اللي اتضافت بيه.
 */
fun payerAccountsOf(source: IncomeSource, seen: Map<String, TransferPartyRef>): List<PayerAccount> = partyPayerKeys(source).map { key ->
    val ref = seen[key]
    val last4 = ref?.last4 ?: last4OfPartyKey(key)
    PayerAccount(key, ref?.label ?: last4?.let { "••••$it" } ?: key, last4)
}

/** طرف بيحوّل لك فلوس: كام تحويل داخل منه وآخر يوم — لاختيار «الحسابات المحوِّلة» من غير كتابة. */
data class IncomingSender(val party: TransferPartyRef, val count: Int, val lastDate: IsoDate)

/** الأطراف اللي حوّلت لك (الداخل بس)، الأحدث الأول — الاسم من آخر عملية. */
fun incomingSenders(transactions: List<Transaction>): List<IncomingSender> {
    val byKey = LinkedHashMap<String, IncomingSender>()
    for (t in transactions.sortedWith(compareBy({ it.occurredAt }, { it.sourceOrder }))) {
        if (t.observedDirection != Direction.IN) continue
        val party = transferPartyOf(t) ?: continue
        val prev = byKey[party.key]
        byKey[party.key] = IncomingSender(party, (prev?.count ?: 0) + 1, t.occurredAt)
    }
    return byKey.values.sortedWith(compareByDescending<IncomingSender> { it.lastDate }.thenByDescending { it.count }.thenBy { it.party.key })
}

/** جهة الشغل = مصدر بمرتب (وظيفة · دوام جزئي). */
fun isEmployer(source: IncomeSource): Boolean = source.kind in SALARIED_KINDS

/**
 * الجهة اللي الإيداع ده راتبها: داخل، من حساب من حسابات الجهة، **ويومه جوه فترتها** (شاملة الطرفين). جهتين بنفس الحساب في نفس اليوم ⇒
 * الأحدث بداية. برّه أي فترة ⇒ null (ما بنخمّنش — يتسأل).
 */
fun employerOf(t: Transaction, sources: List<IncomeSource>): IncomeSource? = employerByPayer(t, sources) ?: soleEmployerFor(t, sources)

/**
 * الجهة من **حساب مُحوِّل معروف** أو من **اختيار المالك للعملية دي بالذات** بس — من غير تخمين «الجهة الوحيدة» ([soleEmployerFor]).
 * ده اللي بيأكد «راتب» لوحده ([applyKnownPayerSalary]): التخمين للعرض والسؤال بس، ونوع رسالة الراتب اللي من غير حساب بيقرره رد
 * «ده راتبك؟» (§75-2 — `SmsSalaryEffect`) أو المالك بإيده (مراجعة r051 — التخمين كان بيكتب «راتب» مؤكد فوق رد «لأ»).
 */
fun employerByPayer(t: Transaction, sources: List<IncomeSource>): IncomeSource? {
    if (t.observedDirection != Direction.IN) return null
    // r05 (البند 9): الراتب اللي اختار المالك جهته بإيده (مفتاح العملية نفسها) أولًا
    sources.firstOrNull { isEmployer(it) && salaryTxnKey(t.id) in it.payerKeys }?.let { return it }
    val key = transferPartyOf(t)?.key ?: return null
    return sourcesActiveOn(sources, t.occurredAt).filter { key in it.payerKeys }.maxByOrNull { it.startedAt }
}

private const val SALARY_TXN_PREFIX = "txn:"

/** مفتاح «الراتب ده من الجهة دي» على عملية من غير حساب مُحوِّل (رسالة «حوالة واردة راتب») — جوه `payerKeys` (أو `declinedPayerKeys` لـ«مش دي»). */
fun salaryTxnKey(transactionId: Id): String = "$SALARY_TXN_PREFIX$transactionId"

/** المفتاح ده اختيار جهة لعملية بعينها ([salaryTxnKey]) — مش حساب بيحوّل. */
fun isSalaryTxnKey(key: String): Boolean = key.startsWith(SALARY_TXN_PREFIX)

/** معرّف العملية من [salaryTxnKey]، أو null لو المفتاح حساب. */
fun salaryTxnIdOf(key: String): Id? = if (isSalaryTxnKey(key)) key.removePrefix(SALARY_TXN_PREFIX) else null

/**
 * حسابات الجهة **الحقيقية** (من غير اختيارات العمليات [salaryTxnKey]) — هي اللي بتقول «معروف مين بيحوّل» (تنبيه المرتب المتأخر · قايمة
 * الحسابات · شاشة المصدر). مراجعة r051: الاختيار على عملية من غير حساب كان بيتعد حساب معروف ⇒ «المرتب اتأخر» كل شهر وهو واصل.
 */
fun partyPayerKeys(source: IncomeSource): List<String> = source.payerKeys.filterNot(::isSalaryTxnKey)

// مراجعة r051: «مرتبط/مرتبطة» و«مرتبة» و«مراتب» مش راتب (كانت بتتمسك من غير حدود للكلمة)
private val SALARY_TEXT = Regex("(?<!م)راتب|رواتب|مرتب(?![طة])|SALARY|PAYROLL", RegexOption.IGNORE_CASE)

/** راتب من غير حساب مُحوِّل: داخل، مالوش طرف، ونوعه راتب أو رسالته بتقول راتب. */
fun isPartylessSalary(t: Transaction): Boolean =
    t.observedDirection == Direction.IN && transferPartyOf(t) == null &&
        (t.economicKind == EconomicKind.SALARY || t.rawDescription?.let { SALARY_TEXT.containsMatchIn(it) } == true)

/**
 * r05 (البند 9): راتب من غير حساب ⇒ الجهة الوحيدة الشغالة يومه (لوحدها — «مش دي» بيحطها في `declinedPayerKeys`). جهتين أو مفيش ⇒ null (يتسأل
 * «راتب من أي جهة؟»).
 */
fun soleEmployerFor(t: Transaction, sources: List<IncomeSource>): IncomeSource? {
    if (!isPartylessSalary(t)) return null
    val sole = sourcesActiveOn(sources, t.occurredAt).filter(::isEmployer).singleOrNull() ?: return null
    return sole.takeIf { salaryTxnKey(t.id) !in it.declinedPayerKeys }
}

/**
 * الحساب ده مربوط بجهة **تانية** فترتها بتتقابل مع فترة [employer]؟ (نفس الحساب لجهتين في نفس الوقت مش مفهوم ⇒ بيترفض؛ جهة قديمة
 * وجهة جديدة بنفس الحساب في فترتين منفصلتين مسموح).
 */
fun accountClash(employer: IncomeSource, key: String, sources: List<IncomeSource>): IncomeSource? = sources.firstOrNull {
    it.id != employer.id && key in it.payerKeys && periodsOverlap(it.startedAt, it.endedAt, employer.startedAt, employer.endedAt)
}

/** أنواع ما بتتغيرش لـ«راتب» حتى لو المالك طلب «وغيّر اللي حددته بإيدك»: الراتب نفسه · المكافأة · النقوط · التحويل بين محافظك. */
private val NEVER_RETAGGED: Set<EconomicKind> =
    setOf(EconomicKind.SALARY, EconomicKind.END_OF_SERVICE, EconomicKind.EVENT_GIFT, EconomicKind.INTERNAL_TRANSFER)

/**
 * «طبّق على اللي فات» لجهة (أو لكل الجهات لو [employerId] = null): [toSalary] = إيداعات من حسابات الجهة جوه فترتها نوعها **لسه
 * ما اتأكدش** ⇒ هتبقى «راتب» مؤكد. [handSet] = إيداعات زيها بس المالك **أكد** لها نوع تاني بإيده ⇒ ما بتتلمسش إلا لو اختار ده صراحة.
 * العملية اللي عليها سؤال مستني (§77-D) برّه الاتنين. [skip] = عمليات اتجاوبت في نفس الخطوة.
 */
data class SalaryRetroPlan(val toSalary: List<Transaction>, val handSet: List<Transaction>) {
    val isEmpty: Boolean get() = toSalary.isEmpty() && handSet.isEmpty()
}

fun salaryRetroPlan(
    transactions: List<Transaction>,
    sources: List<IncomeSource>,
    employerId: Id? = null,
    skip: Set<Id> = emptySet(),
): SalaryRetroPlan {
    val toSalary = mutableListOf<Transaction>()
    val handSet = mutableListOf<Transaction>()
    for (t in transactions.sortedWith(compareBy({ it.occurredAt }, { it.sourceOrder }, { it.id }))) {
        if (t.id in skip || awaitsKindAnswer(t)) continue
        // r05 (البند 9): «طبّق على اللي فات» لجهة بعينها بياخد رسايل الراتب اللي من غير حساب في فترتها كمان
        val employer = employerOf(t, sources) ?: employerId?.let { id -> partylessIn(t, sources, id) } ?: continue
        if (employerId != null && employer.id != employerId) continue
        when {
            !t.economicKindConfirmed -> toSalary += t
            t.economicKind in NEVER_RETAGGED -> Unit
            else -> handSet += t
        }
    }
    return SalaryRetroPlan(toSalary, handSet)
}

/** راتب من غير حساب يومه جوه فترة الجهة [employerId] (ومش «مش دي» ليها). */
private fun partylessIn(t: Transaction, sources: List<IncomeSource>, employerId: Id): IncomeSource? {
    if (!isPartylessSalary(t)) return null
    val e = sourcesActiveOn(sources, t.occurredAt).firstOrNull { it.id == employerId && isEmployer(it) } ?: return null
    return e.takeIf { salaryTxnKey(t.id) !in it.declinedPayerKeys }
}

/** الإيداع بعد ما بقى «راتب» مؤكد (التصنيف ما بيتغيرش — زي `applyKnownPayerSalary`). */
fun asConfirmedSalary(t: Transaction, nowIso: String): Transaction =
    t.copy(economicKind = EconomicKind.SALARY, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = nowIso)

/**
 * تصنيفات الدخل (البند 6 — «عمل إضافي» · «بيعة» · «راتب الدوام»): أساسي «الدخل» وتلات فروع، بمعرّفات ثابتة عشان الجهازين يلاقوا نفس
 * التصنيف، وبيتعملوا **لو مش موجودين بس** (زي «هدايا › نقوط» و«رسوم بنكية») في البلد اللي العملية فيها — السعودية ومصر كل واحدة لوحدها.
 * الحقول اللي التطبيق القديم بيطلبها كلها موجودة (الاسم · الرمز · اللونين · شغّال · الترتيب · الأب). الاسم بلغة الواجهة وقت الإنشاء
 * (بيانات بتتخزن — §40)، واسم «راتب»/«مرتب» من أسامي تصنيف الراتب ([SALARY_CATEGORY_NAMES]). ⚠️ الرموز والألوان مؤقتة (§55) — اختيار Claude.
 */
object IncomeCategories {
    const val ROOT = "cat-income"
    const val SALARY = "cat-income-salary"
    const val SIDE_JOB = "cat-income-side-job"
    const val SALE = "cat-income-sale"

    /** بالترتيب: الأب الأول (عشان الفرع ما يتحفظش قبل أبوه). */
    fun defaults(): List<Category> = listOf(
        Category(ROOT, null, uiText(UiKey.EMPLOYER_CAT_INCOME), "banknote", "#1f7a4d", "#8fd6ad", true, 905),
        Category(SALARY, ROOT, uiText(UiKey.EMPLOYER_CAT_SALARY), "briefcase-business", "#1f7a4d", "#8fd6ad", true, 906),
        Category(SIDE_JOB, ROOT, uiText(UiKey.EMPLOYER_CAT_SIDE_JOB), "hammer", "#1f7a4d", "#8fd6ad", true, 907),
        Category(SALE, ROOT, uiText(UiKey.EMPLOYER_CAT_SALE), "store", "#1f7a4d", "#8fd6ad", true, 908),
    )

    /** نوع العملية ⇒ فرعها: «عمل إضافي» = عمل حر · «بيعة» = بيع شخصي · راتب الدوام = راتب. */
    fun forKind(kind: EconomicKind): Id? = when (kind) {
        EconomicKind.SALARY -> SALARY
        EconomicKind.FREELANCE -> SIDE_JOB
        EconomicKind.PERSONAL_SALE -> SALE
        else -> null
    }
}
