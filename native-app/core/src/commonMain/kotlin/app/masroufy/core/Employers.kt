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
fun payerAccountsOf(source: IncomeSource, seen: Map<String, TransferPartyRef>): List<PayerAccount> = source.payerKeys.map { key ->
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
fun employerOf(t: Transaction, sources: List<IncomeSource>): IncomeSource? {
    if (t.observedDirection != Direction.IN) return null
    val key = transferPartyOf(t)?.key ?: return null
    return sourcesActiveOn(sources, t.occurredAt).filter { key in it.payerKeys }.maxByOrNull { it.startedAt }
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
        val employer = employerOf(t, sources) ?: continue
        if (employerId != null && employer.id != employerId) continue
        when {
            !t.economicKindConfirmed -> toSalary += t
            t.economicKind in NEVER_RETAGGED -> Unit
            else -> handSet += t
        }
    }
    return SalaryRetroPlan(toSalary, handSet)
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
