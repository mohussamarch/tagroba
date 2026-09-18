import type { Id } from '../../domain/entities/types'
import type { Project, ProjectLink, ProjectRule } from '../../domain/entities/projectEntities'
import type { ProjectLinkRepository, ProjectRepository, ProjectRuleRepository } from '../../application/ports/ProjectPorts'
import type { Snapshotable } from './memoryRepositories'

const clone = <T>(value: T): T => structuredClone(value)

class Store<T extends { id: Id }> implements Snapshotable<T[]> {
  protected items = new Map<Id, T>()
  constructor(seed: readonly T[] = []) {
    for (const item of seed) this.items.set(item.id, clone(item))
  }
  async listAll(): Promise<T[]> {
    return [...this.items.values()].map(clone)
  }
  async save(item: T): Promise<void> {
    this.items.set(item.id, clone(item))
  }
  snapshot(): T[] {
    return [...this.items.values()].map(clone)
  }
  restore(state: T[]): void {
    this.items = new Map(state.map((item) => [item.id, clone(item)]))
  }
}

/** المشاريع في الذاكرة — للاختبار والمعاينة (CLAUDE.md #6). */
export class MemoryProjectRepository extends Store<Project> implements ProjectRepository {}

export class MemoryProjectRuleRepository extends Store<ProjectRule> implements ProjectRuleRepository {}

export class MemoryProjectLinkRepository extends Store<ProjectLink> implements ProjectLinkRepository {
  async listByTransaction(transactionId: Id): Promise<ProjectLink[]> {
    return [...this.items.values()].filter((l) => l.transactionId === transactionId).map(clone)
  }
  async saveMany(links: readonly ProjectLink[]): Promise<void> {
    for (const link of links) this.items.set(link.id, clone(link))
  }
}
