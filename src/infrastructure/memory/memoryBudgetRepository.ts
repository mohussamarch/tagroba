import type { Budget, CategoryBudget, Id } from '../../domain/entities/types'
import type { BudgetRepository } from '../../application/ports/repositories'
import type { Snapshotable } from './memoryRepositories'

const clone = <T>(value: T): T => structuredClone(value)

/** مستودع ميزانيات في الذاكرة — للاختبار بلا فايربيز (CLAUDE.md #6). */
export class MemoryBudgetRepository
  implements BudgetRepository, Snapshotable<{ budgets: Budget[]; lines: CategoryBudget[] }>
{
  private budgets = new Map<string, Budget>() // بمفتاح الفترة
  private lines = new Map<Id, CategoryBudget>()

  async findByPeriod(periodKey: string): Promise<Budget | null> {
    const found = this.budgets.get(periodKey)
    return found ? clone(found) : null
  }

  async save(budget: Budget): Promise<void> {
    this.budgets.set(budget.periodKey, clone(budget))
  }

  async remove(periodKey: string): Promise<void> {
    const budget = this.budgets.get(periodKey)
    if (!budget) return
    // مسح الميزانية يمسح سقوف تصنيفاتها معها — لا تبقى سطور يتيمة
    for (const [id, line] of this.lines) {
      if (line.budgetId === budget.id) this.lines.delete(id)
    }
    this.budgets.delete(periodKey)
  }

  async listCategoryBudgets(budgetId: Id): Promise<CategoryBudget[]> {
    return [...this.lines.values()].filter((l) => l.budgetId === budgetId).map(clone)
  }

  async saveCategoryBudget(line: CategoryBudget): Promise<void> {
    this.lines.set(line.id, clone(line))
  }

  async removeCategoryBudget(id: Id): Promise<void> {
    this.lines.delete(id)
  }

  snapshot() {
    return {
      budgets: [...this.budgets.values()].map(clone),
      lines: [...this.lines.values()].map(clone),
    }
  }

  restore(state: { budgets: Budget[]; lines: CategoryBudget[] }): void {
    this.budgets = new Map(state.budgets.map((b) => [b.periodKey, clone(b)]))
    this.lines = new Map(state.lines.map((l) => [l.id, clone(l)]))
  }
}
