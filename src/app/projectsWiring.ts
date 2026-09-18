import type { Firestore } from 'firebase/firestore'
import { makeManageProjects } from '../application/useCases/manageProjects'
import type { AllocationRepository, CategoryRepository, TransactionRepository } from '../application/ports/repositories'
import { FirestoreProjectLinkRepository, FirestoreProjectRepository, FirestoreProjectRuleRepository } from '../infrastructure/firestore/projectRepositories'
import { RandomIdGenerator } from '../infrastructure/firestore/randomIdGenerator'
import { localSyncCursor } from '../infrastructure/localSyncCursor'

/** المشاريع على فايربيز (OVERRIDES §34) — برا `container.ts` عشان حد الـ300 سطر. */
export function firestoreProjects(
  db: Firestore,
  uid: string,
  deps: { txns: TransactionRepository; allocations: AllocationRepository; categories: CategoryRepository },
) {
  return makeManageProjects({
    projects: new FirestoreProjectRepository(db, uid),
    links: new FirestoreProjectLinkRepository(db, uid),
    rules: new FirestoreProjectRuleRepository(db, uid),
    ...deps,
    ids: new RandomIdGenerator(),
    clock: { nowIso: () => new Date().toISOString() },
    // مراجعة العمليات الجديدة على الجهاز ده؛ لو اتمسحت بتبدأ من أقدم قاعدة من غير تكرار
    cursor: localSyncCursor(uid, 'project-rules'),
  })
}
