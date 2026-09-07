import type { Id, Person } from '../../domain/entities/types'
import type { PersonRepository } from '../../application/ports/repositories'
import type { Snapshotable } from './memoryRepositories'

const clone = <T>(value: T): T => structuredClone(value)

/** أشخاص في الذاكرة — للاختبار بلا فايربيز (CLAUDE.md #6). */
export class MemoryPersonRepository implements PersonRepository, Snapshotable<Person[]> {
  private items = new Map<Id, Person>()

  constructor(seed: readonly Person[] = []) {
    for (const p of seed) this.items.set(p.id, clone(p))
  }

  async listAll(): Promise<Person[]> {
    return [...this.items.values()].map(clone)
  }

  async save(person: Person): Promise<void> {
    this.items.set(person.id, clone(person))
  }

  snapshot(): Person[] {
    return [...this.items.values()].map(clone)
  }

  restore(state: Person[]): void {
    this.items = new Map(state.map((p) => [p.id, clone(p)]))
  }
}
