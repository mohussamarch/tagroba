package app.masroufy.port

import app.masroufy.core.Id
import app.masroufy.core.Project
import app.masroufy.core.ProjectLink
import app.masroufy.core.ProjectRule

/** المشاريع وروابطها وقواعدها (OVERRIDES §34) — نقل `ProjectPorts.ts`: تعريفات بس. */
interface ProjectRepository {
    suspend fun listAll(): List<Project>

    suspend fun save(project: Project)
}

interface ProjectLinkRepository {
    /** كل الروابط — مئات مش آلاف (عمليات مختارة بإيد المستخدم أو بقاعدته). */
    suspend fun listAll(): List<ProjectLink>

    suspend fun listByTransaction(transactionId: Id): List<ProjectLink>

    suspend fun saveMany(links: List<ProjectLink>)

    /** التراجع عن دفعة استيراد بيشيل روابط العمليات اللي اتمسحت (ما يسيبش ربط بيشاور على عملية مش موجودة). */
    suspend fun deleteMany(ids: List<Id>)
}

interface ProjectRuleRepository {
    suspend fun listAll(): List<ProjectRule>

    suspend fun save(rule: ProjectRule)
}

/** آخر مراجعة على الجهاز ده — مش بيانات مالية، وضياعها بيعمل مراجعة كاملة بس (من غير تكرار). */
interface SyncCursorPort {
    fun read(): String?

    fun write(iso: String)
}
