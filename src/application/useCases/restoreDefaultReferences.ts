import { planReferenceRestore, type ReferenceRestorePlan } from '../../domain/referenceRestore'
import type { Category, ClassificationRule, Merchant, Transaction } from '../../domain/entities/types'
import type { IdPatch, StoredData } from '../../domain/idRepair'
import type { IdRepairPort } from '../ports/IdRepairPort'
import type { RepairBackupPort } from '../ports/RepairBackupPort'
import type { Clock, MerchantRepository, RuleRepository } from '../ports/repositories'
import { REPAIR_CHUNK, type RepairProgress } from './repairStoredIds'
import { saveVerifiedBackup, type VerifiedBackup } from './verifiedBackup'

export interface ReferenceRestoreOutcome {
  rules: number
  merchants: number
  suggested: number
  /** اقتراحات اتغيرت بعد المعاينة (العملية اتصنفت أو اتأكدت) فاتخطت. */
  skipped: number
  backup: VerifiedBackup | null
}

export interface RestoreDefaultReferencesDeps {
  port: IdRepairPort
  rules: RuleRepository
  merchants: MerchantRepository
  backup: RepairBackupPort
  clock: Clock
  /** القواعد والتجار الافتراضيين مبنيين على شجرة التصنيفات (`loadReferences` بالشجرة). */
  defaults: { rules: readonly ClassificationRule[]; merchants: readonly Merchant[] }
}

const rowsOf = <T>(rows: StoredData[keyof StoredData]) => rows.map((row) => ({ ...row.data, id: row.docId }) as T)

/**
 * RestoreDefaultReferences — رجوع القواعد والتجار الافتراضيين لحساب (OVERRIDES §28.1: «رجّعهم بمعاينة»،
 * و«رجّع لحسابي الأول وبعدين المشتركة»).
 *
 * المعاينة قراءة بس. التطبيق بيقرا تاني وبينفذ **المعروض بس**: نسخة متأكدة للعمليات اللي هتاخد اقتراح ←
 * إضافة القواعد والتجار الناقصين ← اقتراح التصنيف للعمليات اللي مالهاش تصنيف (`reviewState: 'suggested'`،
 * **مش مؤكد**، ومن غير لمس المبلغ ولا التاريخ). عملية اتصنفت أو اتأكدت بعد المعاينة بتتخطى.
 */
export function makeRestoreDefaultReferences(deps: RestoreDefaultReferencesDeps) {
  const planFrom = (stored: StoredData) => planReferenceRestore({
    rules: rowsOf<ClassificationRule>(stored.rules),
    merchants: rowsOf<Merchant>(stored.merchants),
    categories: rowsOf<Category>(stored.categories),
    transactions: rowsOf<Transaction>(stored.transactions),
  }, deps.defaults)

  async function preview(): Promise<ReferenceRestorePlan> {
    return planFrom(await deps.port.readAll())
  }

  async function apply(previewed: ReferenceRestorePlan, onProgress?: (progress: RepairProgress) => void): Promise<ReferenceRestoreOutcome> {
    const stored = await deps.port.readAll()
    const fresh = planFrom(stored)

    const approvedRules = new Set(previewed.rules.map((r) => r.id))
    const approvedMerchants = new Set(previewed.merchants.map((m) => m.id))
    const approvedSuggestions = new Set(previewed.suggestions.map((s) => `${s.transactionId}>${s.categoryId}`))
    const rules = fresh.rules.filter((r) => approvedRules.has(r.id))
    const merchants = fresh.merchants.filter((m) => approvedMerchants.has(m.id))
    const suggestions = fresh.suggestions.filter((s) => approvedSuggestions.has(`${s.transactionId}>${s.categoryId}`))
    const skipped = previewed.suggestions.length - suggestions.length
    if (rules.length === 0 && merchants.length === 0 && suggestions.length === 0) {
      return { rules: 0, merchants: 0, suggested: 0, skipped, backup: null }
    }

    const touched = new Set(suggestions.map((s) => s.transactionId))
    const before = stored.transactions.filter((row) => touched.has(row.docId)).map((row) => ({ group: 'transactions', ...row }))
    const saved = await saveVerifiedBackup(deps.backup, deps.clock, 'before-default-rules', before)

    if (rules.length) await deps.rules.saveMany(rules)
    if (merchants.length) await deps.merchants.saveMany(merchants)

    const now = deps.clock.nowIso()
    const patches: IdPatch[] = suggestions.map((s) => ({
      group: 'transactions', docId: s.transactionId, fields: { categoryId: s.categoryId, reviewState: 'suggested', updatedAt: now },
    }))
    let written = 0
    onProgress?.({ written, total: patches.length })
    for (let i = 0; i < patches.length; i += REPAIR_CHUNK) {
      try {
        written += await deps.port.apply(patches.slice(i, i + REPAIR_CHUNK))
      } catch (error) {
        const cause = error instanceof Error ? error.message : String(error)
        throw new Error(`اتضاف ${rules.length} قاعدة و${merchants.length} تاجر، واتقترح تصنيف لـ${written} من ${patches.length} عملية قبل الانقطاع (${cause}).`)
      }
      onProgress?.({ written, total: patches.length })
    }

    return { rules: rules.length, merchants: merchants.length, suggested: written, skipped, backup: saved }
  }

  return { preview, apply }
}
