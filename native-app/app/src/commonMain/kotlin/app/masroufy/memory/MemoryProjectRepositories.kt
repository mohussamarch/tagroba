package app.masroufy.memory

import app.masroufy.core.Id
import app.masroufy.core.Project
import app.masroufy.core.ProjectLink
import app.masroufy.core.ProjectRule
import app.masroufy.port.ProjectLinkRepository
import app.masroufy.port.ProjectRepository
import app.masroufy.port.ProjectRuleRepository
import app.masroufy.port.SyncCursorPort

/**
 * المشاريع في الذاكرة — نقل `memoryProjectRepositories.ts`.
 * الحفظ فوق معرّف موجود بيسيبه في مكانه (زي `Map` في جافاسكربت)، فترتيب القراية يطابق ملف المرجع.
 */
class MemoryProjectRepository(seed: List<Project> = emptyList()) : ProjectRepository {
    private val items = LinkedHashMap<Id, Project>()

    init {
        for (p in seed) items[p.id] = p
    }

    override suspend fun listAll(): List<Project> = items.values.toList()

    override suspend fun save(project: Project) {
        items[project.id] = project
    }
}

class MemoryProjectRuleRepository(seed: List<ProjectRule> = emptyList()) : ProjectRuleRepository {
    private val items = LinkedHashMap<Id, ProjectRule>()

    init {
        for (r in seed) items[r.id] = r
    }

    override suspend fun listAll(): List<ProjectRule> = items.values.toList()

    override suspend fun save(rule: ProjectRule) {
        items[rule.id] = rule
    }
}

class MemoryProjectLinkRepository(seed: List<ProjectLink> = emptyList()) : ProjectLinkRepository {
    private val items = LinkedHashMap<Id, ProjectLink>()

    init {
        for (l in seed) items[l.id] = l
    }

    override suspend fun listAll(): List<ProjectLink> = items.values.toList()

    override suspend fun listByTransaction(transactionId: Id): List<ProjectLink> =
        items.values.filter { it.transactionId == transactionId }

    override suspend fun saveMany(links: List<ProjectLink>) {
        for (l in links) items[l.id] = l
    }
}

class MemorySyncCursor(private var value: String? = null) : SyncCursorPort {
    override fun read(): String? = value

    override fun write(iso: String) {
        value = iso
    }
}
