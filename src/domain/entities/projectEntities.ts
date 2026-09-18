import type { Id, RuleMatchMode } from './types'

/**
 * المشاريع — OVERRIDES §34. **علامة إضافية على العمليات مش تصنيف**: العملية بتفضل بتصنيفها،
 * ومفيش أي مجموع عام بيتغير. المشروع مابيتمسحش — بيتأرشف.
 */
export interface Project {
  id: Id
  name: string
  normalizedName: string
  archived: boolean
  createdAt: string
}

/**
 * - `manual`: المستخدم ضافها بنفسه.
 * - `rule`: قاعدة من قواعد المشروع ضافتها.
 * - `excluded`: المستخدم شالها — بتفضل متسجلة عشان أي قاعدة ما ترجعهاش.
 */
export type ProjectLinkSource = 'manual' | 'rule' | 'excluded'

/** ربط عملية بمشروع. المعرّف ثابت من الاتنين (`projectLinkId`) فمفيش ربطين لنفس العملية في نفس المشروع. */
export interface ProjectLink {
  id: Id
  projectId: Id
  transactionId: Id
  source: ProjectLinkSource
  createdAt: string
}

export type ProjectRuleDirection = 'out' | 'in' | 'any'

/** «كل عملية فيها النص ده تنضاف للمشروع» — للصرف وللفلوس اللي جاية (OVERRIDES §34). */
export interface ProjectRule {
  id: Id
  projectId: Id
  matchText: string
  matchMode: RuleMatchMode
  direction: ProjectRuleDirection
  enabled: boolean
  /** العمليات اللي اتسجلت بعد الوقت ده بتتضاف لوحدها؛ القديمة بسؤال وقت عمل القاعدة. */
  createdAt: string
}
