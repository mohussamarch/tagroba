import type { ReferenceSeedPort } from '../../application/ports/ReferenceSeedPort'
import type { CategoryRepository, RuleRepository, MerchantRepository } from '../../application/ports/repositories'

/** Reuse this instance across retries, just as Firestore retains the marker across launches. */
export function memoryReferenceSeed(repos: {
  categories: CategoryRepository
  rules: RuleRepository
  merchants: MerchantRepository
}): ReferenceSeedPort {
  let state: 'pending' | 'complete' | undefined
  return {
    async begin(hasExistingCategories) {
      state ??= hasExistingCategories ? 'complete' : 'pending'
      return state
    },
    async insertMissing(source) {
      if (state === 'complete') return
      if (!state) throw new Error('تجهيز المراجع لم يبدأ')
      const categories = new Set((await repos.categories.listAll()).map(row => row.id))
      for (const row of source.categories) if (!categories.has(row.id)) await repos.categories.save(row)
      const rules = new Set((await repos.rules.listAll()).map(row => row.id))
      await repos.rules.saveMany(source.rules.filter(row => !rules.has(row.id)))
      const merchants = new Set((await repos.merchants.listAll()).map(row => row.id))
      await repos.merchants.saveMany(source.merchants.filter(row => !merchants.has(row.id)))
    },
    async complete() {
      if (!state) throw new Error('تجهيز المراجع لم يبدأ')
      state = 'complete'
    },
  }
}
