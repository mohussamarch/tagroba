package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IncomeFollowUp
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceError
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.IsoDate
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
 */
data class ManageIncomeSourcesDeps(
    val sources: IncomeSourceRepository,
    /** تغيير بداية الشهر المالي بيعدّي على استخدام الملف نفسه (مش كتابة مباشرة). */
    val profile: ManageProfile,
    val uow: UnitOfWork,
    val ids: IdGenerator,
    val clock: Clock,
)

/** اللي المستخدم بيكتبه: الاسم وتاريخ البداية بس إجباريين، والباقي اختياري (مفيش فورم طويل). */
data class IncomeSourceInput(
    val name: String,
    val startedAt: IsoDate,
    val kind: IncomeSourceKind = IncomeSourceKind.JOB,
    val currency: Currency = Currency.SAR,
    val expectedDayOfMonth: Int? = null,
    val expectedMinor: Halalas? = null,
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

    private fun build(input: IncomeSourceInput, all: List<IncomeSource>, id: Id, createdAt: String): IncomeSource {
        val checked = checkIncomeSource(input.name, input.startedAt, null, input.expectedDayOfMonth, input.expectedMinor, all)
        return IncomeSource(
            id, checked.name, checked.normalizedName, input.kind, input.currency, input.startedAt, null,
            input.expectedDayOfMonth, input.expectedMinor, createdAt,
        )
    }

    /** تسجيل مصدر (شغلك الحالي مثلًا) — من غير «مبروك» ولا أسئلة؛ الشغل **الجديد** بيعدّي على [changeJob]. */
    suspend fun add(input: IncomeSourceInput): IncomeSource {
        val source = build(input, deps.sources.listAll(), deps.ids.next("inc"), deps.clock.nowIso())
        deps.sources.saveMany(listOf(source))
        return source
    }

    /** التعديل بيسيب تاريخ النهاية والأطراف المتعلَّمة زي ما هم. */
    suspend fun edit(id: Id, input: IncomeSourceInput): IncomeSource {
        val all = deps.sources.listAll()
        val old = find(id)
        val checked = checkIncomeSource(input.name, input.startedAt, old.endedAt, input.expectedDayOfMonth, input.expectedMinor, all, selfId = id)
        val updated = old.copy(
            name = checked.name, normalizedName = checked.normalizedName, kind = input.kind, currency = input.currency,
            startedAt = input.startedAt, expectedDayOfMonth = input.expectedDayOfMonth, expectedMinor = input.expectedMinor,
        )
        deps.sources.saveMany(listOf(updated))
        return updated
    }

    /** قفل مصدر لوحده — **من غير أي كلام** (ممكن يكون اتفصل)، سؤال الأثر بس (مكافأة نهاية الخدمة). */
    suspend fun close(id: Id, endedAt: IsoDate): JobChangeResult = changeJob(JobChange(endingId = id, endedAt = endedAt))

    /**
     * «غيّرت شغلي» في خطوة واحدة: القديم بيتقفل والجديد بيتفتح مع بعض أو ولا واحد (وحدة عمل). الأسئلة: مكافأة نهاية الخدمة
     * (وظيفة في السعودية) · يوم المرتب الجديد · «تغيّر بداية شهرك المالي؟» لو مختلف · المرتب المتوقع (اختياري). **مفيش سؤال مواصلات.**
     */
    suspend fun changeJob(change: JobChange): JobChangeResult {
        if (change.endingId == null && change.starting == null) throw IncomeSourceError(uiText(TextKey.INCOME_CHANGE_EMPTY))
        val all = deps.sources.listAll()
        val closed = change.endingId?.let { id ->
            val old = all.firstOrNull { it.id == id } ?: throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_NOT_FOUND))
            if (old.endedAt != null) throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_ALREADY_CLOSED))
            val end = change.endedAt ?: throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_BAD_END))
            checkIncomeSource(old.name, old.startedAt, end, old.expectedDayOfMonth, old.expectedMinor, all, selfId = old.id)
            old.copy(endedAt = end)
        }
        val opened = change.starting?.let { build(it, all, deps.ids.next("inc"), deps.clock.nowIso()) }
        deps.uow.run { deps.sources.saveMany(listOfNotNull(closed, opened)) }
        val outcome = jobChangeOutcome(closed, opened, deps.profile.load().payday)
        return JobChangeResult(closed, opened, outcome.congratulate, outcome.followUps)
    }

    /** رد «المرتب الجديد بينزل يوم كام؟» ⇒ لو مختلف عن بداية الشهر المالي بيرجع «تغيّر بداية شهرك المالي؟». */
    suspend fun answerPayday(sourceId: Id, day: Int): List<IncomeFollowUp> {
        val all = deps.sources.listAll()
        val s = find(sourceId)
        checkIncomeSource(s.name, s.startedAt, s.endedAt, day, s.expectedMinor, all, selfId = s.id)
        val updated = s.copy(expectedDayOfMonth = day)
        deps.sources.saveMany(listOf(updated))
        return listOfNotNull(monthStartFollowUp(updated, deps.profile.load().payday))
    }

    /** رد «المرتب المتوقع كام؟» (اختياري — التخطي = ما تناديش). مش بيتقارن بأي إيداع (§48: ما بنستنتجش زيادة ولا خصم). */
    suspend fun answerExpectedSalary(sourceId: Id, amountMinor: Halalas): IncomeSource {
        val all = deps.sources.listAll()
        val s = find(sourceId)
        checkIncomeSource(s.name, s.startedAt, s.endedAt, s.expectedDayOfMonth, amountMinor, all, selfId = s.id)
        val updated = s.copy(expectedMinor = amountMinor)
        deps.sources.saveMany(listOf(updated))
        return updated
    }

    /** «أيوه، غيّر بداية شهري» ⇒ يوم الراتب في الملف، من نفس استخدام الملف (بيتأكد من القيمة قبل الحفظ). */
    suspend fun applyMonthStart(day: Int): ProfileCheck = deps.profile.save(deps.profile.load().copy(payday = day))
}
