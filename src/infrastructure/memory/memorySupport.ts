import type { Clock, IdGenerator, UnitOfWork } from '../../application/ports/repositories'
import type { Id } from '../../domain/entities/types'
import type { Snapshotable } from './memoryRepositories'

/** أدوات مساعدة للاختبار: وحدة العمل والمعرّفات والساعة. */

/**
 * وحدة عمل ذرّية فعليًا: تلتقط لقطة من كل مستودع قبل البدء،
 * وتستعيدها كاملة عند أي فشل — فلا تبقى نصف دفعة.
 *
 * تثبت حالة spec/06: «انقطاع أثناء حفظ دفعة ⇒ صفر أو كامل الدفعة».
 */
export class MemoryUnitOfWork implements UnitOfWork {
  /**
   * كل مستودع يلتقط نوع لقطة مختلفًا، والوحدة تخزّنها وتعيدها كما هي
   * بلا أن تعرف شكلها — لذلك `unknown` هو النوع الصحيح هنا.
   */
  constructor(private readonly stores: readonly Snapshotable<unknown>[]) {}

  async run<T>(work: () => Promise<T>): Promise<T> {
    const snapshots = this.stores.map((s) => s.snapshot())
    try {
      return await work()
    } catch (error) {
      // الاستعادة بالترتيب العكسي ليس مهمًا هنا لأن اللقطات مستقلة،
      // لكن الاستعادة **كلها** مهمة: مستودع واحد لم يُستعد = نصف دفعة
      this.stores.forEach((store, i) => store.restore(snapshots[i]))
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
