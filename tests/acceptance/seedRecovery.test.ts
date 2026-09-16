import { expect, it, vi } from 'vitest'
import { makeSeedUserReferences, type SeedSource } from '../../src/application/useCases/seedUserReferences'
import { memoryReferenceSeed } from '../../src/infrastructure/memory/referenceSeed'
import { MemoryCategoryRepository, MemoryRuleRepository, MemoryMerchantRepository } from '../../src/infrastructure/memory/memoryRepositories'

const source: SeedSource = {
  categories: [{ id: 'food', parentId: null, name: 'طعام', lightColor: '#008800', darkColor: '#008800', iconKey: 'food', active: true, order: 1 }],
  rules: [{ id: 'rule', matchText: 'SHOP', matchMode: 'contains', categoryId: 'food', priority: 1, enabled: true }],
  merchants: [{ id: 'shop', normalizedName: 'SHOP', displayName: 'Shop' }],
}

function system() {
  const repos = { categories: new MemoryCategoryRepository([]), rules: new MemoryRuleRepository([]), merchants: new MemoryMerchantRepository([]) }
  const progress = memoryReferenceSeed(repos)
  return { ...repos, progress, reopen: () => makeSeedUserReferences({ ...repos, progress }) }
}

it.each(['categories', 'rules', 'merchants', 'complete'] as const)('resumes after failure in %s, even from a new use case instance', async stage => {
  const sys = system()
  const error = new Error('connection lost')
  if (stage === 'complete') vi.spyOn(sys.progress, 'complete').mockRejectedValueOnce(error)
  else if (stage === 'categories') vi.spyOn(sys.categories, 'save').mockRejectedValueOnce(error)
  else if (stage === 'rules') vi.spyOn(sys.rules, 'saveMany').mockRejectedValueOnce(error)
  else vi.spyOn(sys.merchants, 'saveMany').mockRejectedValueOnce(error)
  await expect(sys.reopen()(source)).rejects.toThrow('connection lost')
  await expect(sys.reopen()(source)).resolves.toMatchObject({ seeded: true })
  expect(await sys.categories.listAll()).toEqual(source.categories)
  expect(await sys.rules.listAll()).toEqual(source.rules)
  expect(await sys.merchants.listAll()).toEqual(source.merchants)
  expect((await sys.reopen()(source)).seeded).toBe(false)
})

it('does not overwrite an edited reference when resuming a partial seed', async () => {
  const sys = system()
  vi.spyOn(sys.rules, 'saveMany').mockRejectedValueOnce(new Error('interrupted'))
  await expect(sys.reopen()(source)).rejects.toThrow()
  await sys.categories.save({ ...source.categories[0], name: 'اسمي الخاص' })
  await sys.reopen()(source)
  expect((await sys.categories.listAll())[0].name).toBe('اسمي الخاص')
  expect(await sys.rules.listAll()).toHaveLength(1)
})

it('never restores a deliberately deleted rule after completion', async () => {
  const sys = system()
  await sys.reopen()(source)
  await sys.rules.deleteMany(['rule'])
  await sys.reopen()(source)
  expect(await sys.rules.listAll()).toEqual([])
})

it('adopts legacy accounts without guessing whether absent defaults were deleted', async () => {
  const sys = system()
  await sys.categories.save(source.categories[0])
  expect((await sys.reopen()(source)).seeded).toBe(false)
  expect(await sys.rules.listAll()).toEqual([])
  expect(await sys.merchants.listAll()).toEqual([])
})
