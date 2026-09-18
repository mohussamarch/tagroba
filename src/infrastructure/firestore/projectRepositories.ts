import { collection, doc, getDocs, query, setDoc, where, writeBatch, type Firestore } from 'firebase/firestore'
import type { Id } from '../../domain/entities/types'
import type { Project, ProjectLink, ProjectRule } from '../../domain/entities/projectEntities'
import type { ProjectLinkRepository, ProjectRepository, ProjectRuleRepository } from '../../application/ports/ProjectPorts'

/**
 * المشاريع على Firestore — OVERRIDES §34. تحت `users/{uid}/…` فالقواعد العامة للمستخدم بتغطيها
 * (مفيش نشر قواعد جديد). كل استعلام بحقل واحد (ARCHITECTURE §12).
 */

const BATCH_LIMIT = 500
const path = (uid: string, ...segments: string[]) => ['users', uid, ...segments].join('/')

export class FirestoreProjectRepository implements ProjectRepository {
  constructor(private readonly db: Firestore, private readonly uid: string) {}
  async listAll(): Promise<Project[]> {
    return (await getDocs(collection(this.db, path(this.uid, 'projects')))).docs.map((d) => d.data() as Project)
  }
  async save(project: Project): Promise<void> {
    await setDoc(doc(this.db, path(this.uid, 'projects', project.id)), project)
  }
}

export class FirestoreProjectRuleRepository implements ProjectRuleRepository {
  constructor(private readonly db: Firestore, private readonly uid: string) {}
  async listAll(): Promise<ProjectRule[]> {
    return (await getDocs(collection(this.db, path(this.uid, 'projectRules')))).docs.map((d) => d.data() as ProjectRule)
  }
  async save(rule: ProjectRule): Promise<void> {
    await setDoc(doc(this.db, path(this.uid, 'projectRules', rule.id)), rule)
  }
}

export class FirestoreProjectLinkRepository implements ProjectLinkRepository {
  constructor(private readonly db: Firestore, private readonly uid: string) {}
  async listAll(): Promise<ProjectLink[]> {
    return (await getDocs(collection(this.db, path(this.uid, 'projectLinks')))).docs.map((d) => d.data() as ProjectLink)
  }
  async listByTransaction(transactionId: Id): Promise<ProjectLink[]> {
    const snap = await getDocs(query(collection(this.db, path(this.uid, 'projectLinks')), where('transactionId', '==', transactionId)))
    return snap.docs.map((d) => d.data() as ProjectLink)
  }
  async saveMany(links: readonly ProjectLink[]): Promise<void> {
    for (let i = 0; i < links.length; i += BATCH_LIMIT) {
      const batch = writeBatch(this.db)
      for (const link of links.slice(i, i + BATCH_LIMIT)) batch.set(doc(this.db, path(this.uid, 'projectLinks', link.id)), link)
      await batch.commit()
    }
  }
}
