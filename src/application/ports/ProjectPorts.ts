import type { Id } from '../../domain/entities/types'
import type { Project, ProjectLink, ProjectRule } from '../../domain/entities/projectEntities'

/** المشاريع وروابطها وقواعدها (OVERRIDES §34) — تعريفات بس؛ التنفيذ في firestore/ وmemory/. */
export interface ProjectRepository {
  listAll(): Promise<Project[]>
  save(project: Project): Promise<void>
}

export interface ProjectLinkRepository {
  /** كل الروابط — مئات مش آلاف (عمليات مختارة بإيد المستخدم أو بقاعدته). */
  listAll(): Promise<ProjectLink[]>
  listByTransaction(transactionId: Id): Promise<ProjectLink[]>
  saveMany(links: readonly ProjectLink[]): Promise<void>
}

export interface ProjectRuleRepository {
  listAll(): Promise<ProjectRule[]>
  save(rule: ProjectRule): Promise<void>
}
