import type { Halalas } from '../../domain/money'
import type { Budget, CategoryBudget, Id } from '../../domain/entities/types'
import type { Period } from '../../domain/period'
import type { BudgetRepository, Clock, IdGenerator, UnitOfWork } from '../ports/repositories'

/**
 * SetBudget — ضبط السقوف ومسحها.
 *
 * spec/01: «**لا تُخلق ميزانيات من متوسطات دون اختيار المستخدم**».
 * لذلك **لا توجد هنا دالة تنشئ سقفًا من متوسط**. كل مسار يبدأ
 * بمبلغ يكتبه المستخدم بنفسه.
 *
 * ARCHITECTURE.md §7: معيار اكتمال المرحلة «سقف **يُضبط ويُمسح** ويُقارن»
 * — فالمسح مطلب لا تحسين.
 */

export class BudgetError extends Error {}

export interface SetBudgetDeps {
  budgets: BudgetRepository
  uow: UnitOfWork
  ids: IdGenerator
  clock: Clock
}

/** عتبة التنبيه: نسبة مئوية بين 1 و 100، أو null فلا تنبيه. */
function validateThreshold(percent: number | null): void {
  if (percent === null) return
  if (!Number.isInteger(percent) || percent < 1 || percent > 100) {
    throw new BudgetError('عتبة التنبيه لازم تكون رقم صحيح بين 1 و100')
  }
}

function validateLimit(limitMinor: Halalas): void {
  if (!Number.isInteger(limitMinor)) throw new BudgetError('السقف لازم يكون بالهللة كعدد صحيح')
  if (limitMinor <= 0) throw new BudgetError('السقف لازم يكون أكبر من صفر')
}

export function makeSetBudget(deps: SetBudgetDeps) {
  /** ينشئ ميزانية الفترة إن لم تكن موجودة. لا يضع أي سقف من تلقائه. */
  async function ensureBudget(period: Period): Promise<Budget> {
    const existing = await deps.budgets.findByPeriod(period.key)
    if (existing) return existing

    const now = deps.clock.nowIso()
    const budget: Budget = {
      id: deps.ids.next('budget'),
      periodKey: period.key,
      periodStart: period.start,
      periodEnd: period.end,
      totalLimitMinor: null, // ← بلا سقف حتى يحدده المستخدم
      thresholdPercent: null,
      createdAt: now,
      updatedAt: now,
    }
    await deps.budgets.save(budget)
    return budget
  }

  /** يضبط السقف الإجمالي للفترة بمبلغ **يكتبه المستخدم**. */
  async function setTotalLimit(
    period: Period,
    limitMinor: Halalas,
    thresholdPercent: number | null,
  ): Promise<Budget> {
    validateLimit(limitMinor)
    validateThreshold(thresholdPercent)

    return deps.uow.run(async () => {
      const budget = await ensureBudget(period)
      const updated: Budget = {
        ...budget,
        totalLimitMinor: limitMinor,
        thresholdPercent,
        updatedAt: deps.clock.nowIso(),
      }
      await deps.budgets.save(updated)
      return updated
    })
  }

  /**
   * يمسح السقف الإجمالي **ويُبقي سقوف التصنيفات**.
   *
   * المسح يعيد الحقل إلى `null` لا إلى صفر: الصفر سقف يمنع كل صرف،
   * و`null` يعني «مفيش سقف» — والفرق بينهما كل شيء (spec/06).
   */
  async function clearTotalLimit(period: Period): Promise<void> {
    const budget = await deps.budgets.findByPeriod(period.key)
    if (!budget) return // لا شيء يُمسح

    await deps.budgets.save({
      ...budget,
      totalLimitMinor: null,
      thresholdPercent: null,
      updatedAt: deps.clock.nowIso(),
    })
  }

  /** يضبط سقف تصنيف. */
  async function setCategoryLimit(
    period: Period,
    categoryId: Id,
    limitMinor: Halalas,
    options: { notifyEnabled?: boolean; thresholdPercent?: number | null } = {},
  ): Promise<CategoryBudget> {
    validateLimit(limitMinor)
    const thresholdPercent = options.thresholdPercent ?? null
    validateThreshold(thresholdPercent)

    return deps.uow.run(async () => {
      const budget = await ensureBudget(period)
      const existing = (await deps.budgets.listCategoryBudgets(budget.id)).find(
        (cb) => cb.categoryId === categoryId,
      )

      const line: CategoryBudget = {
        id: existing?.id ?? deps.ids.next('catbudget'),
        budgetId: budget.id,
        categoryId,
        limitMinor,
        notifyEnabled: options.notifyEnabled ?? thresholdPercent !== null,
        thresholdPercent,
      }
      await deps.budgets.saveCategoryBudget(line)
      return line
    })
  }

  async function clearCategoryLimit(period: Period, categoryId: Id): Promise<void> {
    const budget = await deps.budgets.findByPeriod(period.key)
    if (!budget) return

    const existing = (await deps.budgets.listCategoryBudgets(budget.id)).find(
      (cb) => cb.categoryId === categoryId,
    )
    if (existing) await deps.budgets.removeCategoryBudget(existing.id)
  }

  /** يمسح ميزانية الفترة بالكامل: السقف الإجمالي وكل سقوف التصنيفات. */
  async function clearAll(period: Period): Promise<void> {
    await deps.budgets.remove(period.key)
  }

  /**
   * ينسخ سقوف فترة سابقة إلى الفترة الحالية — **بطلب صريح من المستخدم**.
   *
   * هذه ليست إنشاءً من متوسط: المصدر سقوف كتبها المستخدم بنفسه من قبل،
   * والنسخ فعل يطلبه لا يحدث تلقائيًا.
   */
  async function copyFrom(sourcePeriodKey: string, target: Period): Promise<number> {
    const source = await deps.budgets.findByPeriod(sourcePeriodKey)
    if (!source) throw new BudgetError(`مفيش ميزانية للفترة ${sourcePeriodKey} تتنسخ`)

    const sourceLines = await deps.budgets.listCategoryBudgets(source.id)

    return deps.uow.run(async () => {
      const budget = await ensureBudget(target)
      const now = deps.clock.nowIso()

      await deps.budgets.save({
        ...budget,
        totalLimitMinor: source.totalLimitMinor,
        thresholdPercent: source.thresholdPercent,
        updatedAt: now,
      })

      for (const line of sourceLines) {
        await deps.budgets.saveCategoryBudget({
          ...line,
          id: deps.ids.next('catbudget'),
          budgetId: budget.id,
        })
      }
      return sourceLines.length
    })
  }

  return {
    setTotalLimit,
    clearTotalLimit,
    setCategoryLimit,
    clearCategoryLimit,
    clearAll,
    copyFrom,
  }
}
