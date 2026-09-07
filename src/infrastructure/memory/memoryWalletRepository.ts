import type { Id, Wallet } from '../../domain/entities/types'
import type { WalletRepository } from '../../application/ports/repositories'
import type { Snapshotable } from './memoryRepositories'

const clone = <T>(value: T): T => structuredClone(value)

/** محافظ في الذاكرة — للاختبار بلا فايربيز (CLAUDE.md #6). */
export class MemoryWalletRepository
  implements WalletRepository, Snapshotable<Wallet[]>
{
  private items = new Map<Id, Wallet>()

  constructor(seed: readonly Wallet[] = []) {
    for (const w of seed) this.items.set(w.id, clone(w))
  }

  async listAll(): Promise<Wallet[]> {
    return [...this.items.values()].map(clone)
  }

  async findById(id: Id): Promise<Wallet | null> {
    const found = this.items.get(id)
    return found ? clone(found) : null
  }

  async save(wallet: Wallet): Promise<void> {
    this.items.set(wallet.id, clone(wallet))
  }

  snapshot(): Wallet[] {
    return [...this.items.values()].map(clone)
  }

  restore(state: Wallet[]): void {
    this.items = new Map(state.map((w) => [w.id, clone(w)]))
  }
}
