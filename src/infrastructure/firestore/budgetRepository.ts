import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  where,
  writeBatch,
  type Firestore,
} from 'firebase/firestore'
import type { Budget, CategoryBudget, Id } from '../../domain/entities/types'
import type { BudgetRepository } from '../../application/ports/repositories'

/**
 * الميزانيات على Firestore.
 *
 * **معرّف المستند = مفتاح الفترة** ("2026-09"): فترة واحدة لها ميزانية
 * واحدة بحكم البنية، فيستحيل وجود ميزانيتين متعارضتين لنفس الفترة،
 * والقراءة `getDoc` واحدة لا استعلامًا (ARCHITECTURE.md §5.6).
 *
 * سقوف التصنيفات في مجموعة منفصلة باستعلام حقل واحد — لا فهرس مركّب (§12).
 */

function userPath(uid: string, ...segments: string[]): string {
  return ['users', uid, ...segments].join('/')
}

function clean<T extends object>(value: T): T {
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(value)) if (v !== undefined) out[k] = v
  return out as T
}

export class FirestoreBudgetRepository implements BudgetRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  private linesCol() {
    return collection(this.db, userPath(this.uid, 'categoryBudgets'))
  }

  async findByPeriod(periodKey: string): Promise<Budget | null> {
    const snap = await getDoc(doc(this.db, userPath(this.uid, 'budgets', periodKey)))
    return snap.exists() ? (snap.data() as Budget) : null
  }

  async save(budget: Budget): Promise<void> {
    await setDoc(doc(this.db, userPath(this.uid, 'budgets', budget.periodKey)), clean(budget))
  }

  async remove(periodKey: string): Promise<void> {
    const budget = await this.findByPeriod(periodKey)
    if (!budget) return

    // السطور أولًا ثم الميزانية: لو انقطع بينهما تبقى الميزانية فتُعاد
    // المحاولة، ولا تبقى سطور يتيمة بلا ميزانية تشير إليها
    const lines = await this.listCategoryBudgets(budget.id)
    if (lines.length > 0) {
      const batch = writeBatch(this.db)
      for (const line of lines) {
        batch.delete(doc(this.db, userPath(this.uid, 'categoryBudgets', line.id)))
      }
      await batch.commit()
    }
    await deleteDoc(doc(this.db, userPath(this.uid, 'budgets', periodKey)))
  }

  async listCategoryBudgets(budgetId: Id): Promise<CategoryBudget[]> {
    const snap = await getDocs(query(this.linesCol(), where('budgetId', '==', budgetId)))
    return snap.docs.map((d) => d.data() as CategoryBudget)
  }

  async saveCategoryBudget(line: CategoryBudget): Promise<void> {
    await setDoc(doc(this.db, userPath(this.uid, 'categoryBudgets', line.id)), clean(line))
  }

  async removeCategoryBudget(id: Id): Promise<void> {
    await deleteDoc(doc(this.db, userPath(this.uid, 'categoryBudgets', id)))
  }
}
