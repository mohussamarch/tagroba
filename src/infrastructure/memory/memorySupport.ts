import type { Clock, IdGenerator, UnitOfWork } from '../../application/ports/repositories'
import type { Id } from '../../domain/entities/types'

/** أدوات مساعدة للاختبار: وحدة العمل والمعرّفات والساعة. */
/**
 * وحدة عمل تحاكي الذرية: تلتقط لقطة قبل البدء وتستعيدها عند الفشل.
 * تثبت حالة spec/06: «انقطاع أثناء حفظ دفعة ⇒ صفر أو كامل الدفعة».
 */
export class MemoryUnitOfWork implements UnitOfWork {
  constructor(private readonly snapshotters: readonly { snapshot(): unknown; restore(s: unknown): void }[]) {}

  async run<T>(work: () => Promise<T>): Promise<T> {
    const snapshots = this.snapshotters.map((s) => s.snapshot())
    try {
      return await work()
    } catch (error) {
      this.snapshotters.forEach((s, i) => s.restore(snapshots[i]))
      throw error
    }
  }
}

/** وحدة عمل بلا تراجع — للاختبارات التي لا تحتاج محاكاة الفشل. */
export class PassthroughUnitOfWork implements UnitOfWork {
  async run<T>(work: () => Promise<T>): Promise<T> {
    return work()
  }
}

export class SequentialIdGenerator implements IdGenerator {
  private counters = new Map<string, number>()
  next(prefix: string): Id {
    const n = (this.counters.get(prefix) ?? 0) + 1
    this.counters.set(prefix, n)
    return `${prefix}-${String(n).padStart(6, '0')}`
  }
}

export class FixedClock implements Clock {
  constructor(private readonly iso: string) {}
  nowIso(): string {
    return this.iso
  }
}
