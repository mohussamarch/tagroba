package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.IncomeSource

/**
 * r06-archive (OVERRIDES §118-ب-1 — قرار المالك): «أرشفه» للخطة والجمعية ومصدر الدخل. الأرشفة = حقل `archivedAt` (اختياري جديد).
 * - المؤرشف بيختفي من القوايم الشغالة (المستحقات · الدخل · الرئيسية · التنبيهات) وبيفضل في التاريخ والمجاميع — العمليات ما بتتلمسش.
 * - **مصدر الدخل المؤرشف بيتحسب منتهي:** لو كان شغال، `endedAt` بيتقفل النهارده (أو يوم بدايته لو لسه ما بدأش) و`endedByArchive` = true؛
 *   «رجّعه» بيفتحه تاني. لو كان منتهي قبلها، `endedAt` ما بيتلمسش.
 * - **مفيش كتابة لو مفيش تغيير** (مؤرشف وطلبت أرشفة ⇒ null).
 * - كل طريق ليه «تراجع» ([RemovalDone.undo]) بيرجّع المستند زي ما كان بالظبط — نفس شريط المسح.
 */
interface EntityArchiver {
    /** [archived] true = أرشفه · false = رجّعه. null = كان كده أصلًا (ما اتكتبش حاجة). */
    suspend fun setArchived(id: Id, archived: Boolean): RemovalDone?
}

/** اللي بيتعرض في سطر «المؤرشف» تحت القايمة. */
data class ArchivedItem(val id: Id, val name: String)

private fun done(archived: Boolean, name: String, undo: suspend () -> Unit) =
    RemovalDone(if (archived) RemoveChoice.ARCHIVE else RemoveChoice.UNARCHIVE, name, undoWork = undo)

class ArchivePlan internal constructor(private val d: RemovalDeps) : EntityArchiver {
    override suspend fun setArchived(id: Id, archived: Boolean): RemovalDone? {
        val p = d.links.installmentPlans.listAll().firstOrNull { it.id == id } ?: removalGone()
        if (p.archived == archived) return null
        d.links.installmentPlans.save(p.copy(archivedAt = if (archived) d.clock.nowIso() else null))
        return done(archived, p.name) { d.links.installmentPlans.save(p) }
    }
}

class ArchiveRosca internal constructor(private val d: RemovalDeps) : EntityArchiver {
    override suspend fun setArchived(id: Id, archived: Boolean): RemovalDone? {
        val r = d.roscas.listAll().firstOrNull { it.id == id } ?: removalGone()
        if (r.archived == archived) return null
        d.roscas.save(r.copy(archivedAt = if (archived) d.clock.nowIso() else null))
        return done(archived, r.name) { d.roscas.save(r) }
    }
}

class ArchiveIncomeSource internal constructor(private val d: RemovalDeps) : EntityArchiver {
    override suspend fun setArchived(id: Id, archived: Boolean): RemovalDone? {
        val s = d.incomeSources.listAll().firstOrNull { it.id == id } ?: removalGone()
        if (s.archived == archived) return null
        d.incomeSources.saveMany(listOf(archivedSource(s, archived, d.clock.nowIso())))
        return done(archived, s.name) { d.incomeSources.saveMany(listOf(s)) }
    }
}

/** الأرشفة بتقفل المصدر الشغال (منتهي) و«رجّعه» بيفتح اللي الأرشفة قفلته بس. */
internal fun archivedSource(s: IncomeSource, archived: Boolean, nowIso: String): IncomeSource =
    if (archived) {
        val today = nowIso.take(10)
        if (s.endedAt == null) s.copy(archivedAt = nowIso, endedAt = maxOf(today, s.startedAt), endedByArchive = true) else s.copy(archivedAt = nowIso)
    } else {
        if (s.endedByArchive == true) s.copy(archivedAt = null, endedAt = null, endedByArchive = null) else s.copy(archivedAt = null)
    }

/** «المؤرشف» لكل نوع — للشاشات (من غير حساب فيها). */
class Archives internal constructor(d: RemovalDeps) {
    val plan: EntityArchiver = ArchivePlan(d)
    val rosca: EntityArchiver = ArchiveRosca(d)
    val incomeSource: EntityArchiver = ArchiveIncomeSource(d)
}
