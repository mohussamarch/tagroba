package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IncomeFollowUp
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceError
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.IsoDate
import app.masroufy.core.PayFrequency
import app.masroufy.core.ProfileCheck
import app.masroufy.core.TextKey
import app.masroufy.core.checkIncomeSource
import app.masroufy.core.jobChangeOutcome
import app.masroufy.core.monthStartFollowUp
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.IncomeSourceRepository
import app.masroufy.port.UnitOfWork

/**
 * مصادر الدخل (OVERRIDES §48 · §64): إضافة (الاسم وتاريخ البداية بس إجباريين) · تعديل · قفل بتاريخ · «غيّرت شغلي».
 * **مفيش مسح**: المصدر اللي خلص بيتقفل بتاريخ عشان المقارنة مع الشهور القديمة تفضل صح. الشاشات مستنية تصميم المالك.
 * «مبروك» على **بداية** شغل جديد بس؛ القفل لوحده من غير أي كلام (سؤال الأثر بس). **المواصلات ما بتتسألش هنا**.
 * الرجوع لشركة قديمة = **فترة جديدة بنفس الاسم** (رد المالك §64) — المرفوض بس فترتين بنفس الاسم بيتقابلوا.
 */
data class ManageIncomeSourcesDeps(
    val sources: IncomeSourceRepository,
    /** تغيير بداية الشهر المالي بيعدّي على استخدام الملف نفسه (مش كتابة مباشرة). */
    val profile: ManageProfile,
    val uow: UnitOfWork,
    val ids: IdGenerator,
    val clock: Clock,
)

/**
 * اللي المستخدم بيكتبه: الاسم وتاريخ البداية بس إجباريين، والباقي اختياري (مفيش فورم طويل). [payFrequency] شهري افتراضيًا؛
 * الأسبوعي يومه [payWeekday] (1 = الاتنين … 7 = الحد) — §65.
 */
data class IncomeSourceInput(
    val name: String,
    val startedAt: IsoDate,
    val kind: IncomeSourceKind = IncomeSourceKind.JOB,
    val currency: Currency = Currency.SAR,
    val expectedDayOfMonth: Int? = null,
    val expectedMinor: Halalas? = null,
    val payFrequency: PayFrequency = PayFrequency.MONTHLY,
    val payWeekday: Int? = null,
)

/** «غيّرت شغلي»: [endingId] بيتقفل يوم [endedAt]، و/أو [starting] بيتفتح. واحد منهم على الأقل. */
data class JobChange(val endingId: Id? = null, val endedAt: IsoDate? = null, val starting: IncomeSourceInput? = null)

/** [congratulate] أيوه **بس** لو [opened] موجود. [followUps] بالترتيب اللي بيتسأل بيه. */
data class JobChangeResult(
    val closed: IncomeSource?,
    val opened: IncomeSource?,
    val congratulate: Boolean,
    val followUps: List<IncomeFollowUp>,
)

class ManageIncomeSources(private val deps: ManageIncomeSourcesDeps) {
    /** الشغالة الأول، وبعدين الأحدث بداية. */
    suspend fun list(): List<IncomeSource> =
        deps.sources.listAll().sortedWith(compareBy<IncomeSource> { it.endedAt != null }.thenByDescending { it.startedAt }.thenBy { it.id })

    private suspend fun find(id: Id): IncomeSource =
        deps.sources.listAll().firstOrNull { it.id == id } ?: throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_NOT_FOUND))

    /** فحص المصدر كله (الاسم · الفترة · اليوم · الدورية) قدام الباقيين. */
    private fun checked(s: IncomeSource, all: List<IncomeSource>): IncomeSource {
        val name = checkIncomeSource(
            s.name, s.startedAt, s.endedAt, s.expectedDayOfMonth, s.expectedMinor, all, selfId = s.id,
            kind = s.kind, payFrequency = s.payFrequency, payWeekday = s.payWeekday,
        )
        return s.copy(name = name.name, normalizedName = name.normalizedName)
    }

    private fun build(input: IncomeSourceInput, all: List<IncomeSource>, id: Id, createdAt: String): IncomeSource = checked(
        IncomeSource(
            id, input.name, "", input.kind, input.currency, input.startedAt, null, input.expectedDayOfMonth, input.expectedMinor, createdAt,
            payFrequency = input.payFrequency, payWeekday = input.payWeekday,
        ),
        all,
    )

    /** تسجيل مصدر (شغلك الحالي مثلًا) — من غير «مبروك» ولا أسئلة؛ الشغل **الجديد** بيعدّي على [changeJob]. */
    suspend fun add(input: IncomeSourceInput): IncomeSource {
        val source = build(input, deps.sources.listAll(), deps.ids.next("inc"), deps.clock.nowIso())
        deps.sources.saveMany(listOf(source))
        return source
    }

    /** التعديل بيسيب تاريخ النهاية والأطراف المتعلَّمة زي ما هم. */
    suspend fun edit(id: Id, input: IncomeSourceInput): IncomeSource {
        val old = find(id)
        val updated = checked(
            old.copy(
                name = input.name, kind = input.kind, currency = input.currency, startedAt = input.startedAt,
                expectedDayOfMonth = input.expectedDayOfMonth, expectedMinor = input.expectedMinor,
                payFrequency = input.payFrequency, payWeekday = input.payWeekday,
            ),
            deps.sources.listAll(),
        )
        deps.sources.saveMany(listOf(updated))
        return updated
    }

    /** قفل مصدر لوحده — **من غير أي كلام** (ممكن يكون اتفصل)، سؤال الأثر بس (مكافأة نهاية الخدمة). */
    suspend fun close(id: Id, endedAt: IsoDate): JobChangeResult = changeJob(JobChange(endingId = id, endedAt = endedAt))

    /**
     * «غيّرت شغلي» في خطوة واحدة: القديم بيتقفل والجديد بيتفتح مع بعض أو ولا واحد (وحدة عمل). الأسئلة: مكافأة نهاية الخدمة
     * (وظيفة في السعودية) · يوم المرتب الجديد (والبارت تايم: دورية القبض) · «تغيّر بداية شهرك المالي؟» لو مختلف · المرتب المتوقع
     * (اختياري). **مفيش سؤال مواصلات.** الجديد بيتفحص بعد قفل القديم ⇒ ينفع ترجع لنفس الاسم من بعد يوم القفل.
     */
    suspend fun changeJob(change: JobChange): JobChangeResult {
        if (change.endingId == null && change.starting == null) throw IncomeSourceError(uiText(TextKey.INCOME_CHANGE_EMPTY))
        val all = deps.sources.listAll()
        val closed = change.endingId?.let { id ->
            val old = all.firstOrNull { it.id == id } ?: throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_NOT_FOUND))
            if (old.endedAt != null) throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_ALREADY_CLOSED))
            val end = change.endedAt ?: throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_BAD_END))
            checked(old.copy(endedAt = end), all)
        }
        val afterClose = all.map { if (it.id == closed?.id) closed else it }
        val opened = change.starting?.let { build(it, afterClose, deps.ids.next("inc"), deps.clock.nowIso()) }
        deps.uow.run { deps.sources.saveMany(listOfNotNull(closed, opened)) }
        val outcome = jobChangeOutcome(closed, opened, deps.profile.load().payday)
        return JobChangeResult(closed, opened, outcome.congratulate, outcome.followUps)
    }

    /** رد «المرتب الجديد بينزل يوم كام؟» ⇒ لو مختلف عن بداية الشهر المالي بيرجع «تغيّر بداية شهرك المالي؟». */
    suspend fun answerPayday(sourceId: Id, day: Int): List<IncomeFollowUp> {
        val updated = checked(find(sourceId).copy(expectedDayOfMonth = day, payFrequency = PayFrequency.MONTHLY, payWeekday = null), deps.sources.listAll())
        deps.sources.saveMany(listOf(updated))
        return listOfNotNull(monthStartFollowUp(updated, deps.profile.load().payday))
    }

    /**
     * رد «بتقبض إمتى؟ كل شهر ولا كل أسبوع؟» (البارت تايم — §65): [day] يوم في الشهر للشهري (1–31) أو يوم في الأسبوع للأسبوعي
     * (1 = الاتنين … 7 = الحد). **ما بيحرّكش بداية شهرك المالي** (شغل جنب).
     */
    suspend fun answerPayFrequency(sourceId: Id, frequency: PayFrequency, day: Int): IncomeSource {
        val s = find(sourceId)
        val candidate = when (frequency) {
            PayFrequency.MONTHLY -> s.copy(payFrequency = frequency, expectedDayOfMonth = day, payWeekday = null)
            PayFrequency.WEEKLY -> s.copy(payFrequency = frequency, expectedDayOfMonth = null, payWeekday = day)
        }
        val updated = checked(candidate, deps.sources.listAll())
        deps.sources.saveMany(listOf(updated))
        return updated
    }

    /** رد «المرتب المتوقع كام؟» (اختياري — التخطي = ما تناديش). مش بيتقارن بأي إيداع (§48: ما بنستنتجش زيادة ولا خصم). */
    suspend fun answerExpectedSalary(sourceId: Id, amountMinor: Halalas): IncomeSource {
        val updated = checked(find(sourceId).copy(expectedMinor = amountMinor), deps.sources.listAll())
        deps.sources.saveMany(listOf(updated))
        return updated
    }

    /** «أيوه، غيّر بداية شهري» ⇒ يوم الراتب في الملف، من نفس استخدام الملف (بيتأكد من القيمة قبل الحفظ). */
    suspend fun applyMonthStart(day: Int): ProfileCheck = deps.profile.save(deps.profile.load().copy(payday = day))
}
