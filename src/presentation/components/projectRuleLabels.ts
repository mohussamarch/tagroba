import type { RuleMatchMode } from '../../domain/entities/types'
import type { ProjectRuleDirection } from '../../domain/entities/projectEntities'

/** أسماء طرق المطابقة والاتجاه لقواعد المشاريع (OVERRIDES §34). */
export const PROJECT_RULE_MODES: Record<RuleMatchMode, string> = { contains: 'يحتوي', startsWith: 'يبدأ بـ', exact: 'مطابق تمامًا' }
export const PROJECT_RULE_DIRECTIONS: Record<ProjectRuleDirection, string> = { out: 'الصرف بس', in: 'الفلوس اللي جاية بس', any: 'الاتنين' }
