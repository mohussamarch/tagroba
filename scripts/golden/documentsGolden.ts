/**
 * شكل المستندات المتخزنة في فايربيز — ملف مرجع لطبقة التخزين في كوتلن (KOTLIN_PLAN §2.1، OVERRIDES §52).
 *
 * **مش زي باقي ملفات المرجع:** بيحتاج Firestore Emulator شغال، عشان الحكم هو **اللي اتخزن فعلًا**:
 * كل كيان بيتحفظ بمستودع التطبيق الحالي نفسه (`src/infrastructure/firestore/*`) ⇒ بيتقرا خام من المحاكي
 * بالـREST (بنوع كل قيمة: عدد صحيح ولا عشري ولا نص ولا فاضي). كوتلن لازم تكتب **نفس الحقول بنفس الأنواع**
 * عشان التطبيقين يشتغلوا على نفس البيانات جنب بعض.
 *
 * التشغيل (بيانات وهمية، مشروع demo بس، بقواعد `firestore.rules` الحقيقية):
 *   MASROUFY_TEST_FIRESTORE=127.0.0.1:8088 vite-node scripts/golden/documentsGolden.ts
 * ⇒ `native-app/golden/documents.json`. `npm run golden` **ما بيشغّلوش** (مش كل جهاز عليه المحاكي).
 */
import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { initializeApp, deleteApp } from 'firebase/app'
import { connectFirestoreEmulator, getFirestore, terminate, type Firestore } from 'firebase/firestore'
import { FirestoreTransactionRepository, FirestoreSourceRecordRepository, FirestoreImportBatchRepository } from '../../src/infrastructure/firestore/firestoreRepositories'
import { FirestoreWalletRepository } from '../../src/infrastructure/firestore/walletRepository'
import { FirestoreCategoryRepository, FirestoreRuleRepository, FirestoreMerchantRepository } from '../../src/infrastructure/firestore/referenceRepositories'
import { FirestorePersonRepository, FirestoreObligationRepository, FirestoreSettlementRepository, FirestoreAllocationRepository } from '../../src/infrastructure/firestore/peopleRepositories'
import { FirestoreAssetRepository, FirestoreAssetLotRepository, FirestoreAssetSaleRepository, FirestoreAssetPriceRepository } from '../../src/infrastructure/firestore/assetRepositories'
import { FirestoreTagRepository, FirestoreTransactionTagRepository } from '../../src/infrastructure/firestore/tagRepositories'
import { FirestoreBudgetRepository } from '../../src/infrastructure/firestore/budgetRepository'
import { FirestoreRecurringRepository } from '../../src/infrastructure/firestore/recurringRepository'
import { FirestoreNotificationReceiptRepository } from '../../src/infrastructure/firestore/notificationRepository'
import { FirestoreProjectRepository, FirestoreProjectRuleRepository, FirestoreProjectLinkRepository } from '../../src/infrastructure/firestore/projectRepositories'
import { documentSamples, type Samples } from './documentSamples'

const HOST = process.env.MASROUFY_TEST_FIRESTORE
if (HOST !== '127.0.0.1:8088') throw new Error('شغّل Firestore Emulator على 127.0.0.1:8088 وحط MASROUFY_TEST_FIRESTORE=127.0.0.1:8088')
const PROJECT = 'demo-masroufy-docs'
const UID = 'docs-user'

/** كل مجموعة ⇒ الحفظ بمستودع التطبيق الحالي نفسه (نفس المسار اللي الشاشات بتستعمله). */
function writers(db: Firestore): { [K in keyof Samples]: (rows: Samples[K]) => Promise<void> } {
  return {
    wallets: async (rows) => { for (const r of rows) await new FirestoreWalletRepository(db, UID).save(r) },
    categories: async (rows) => { for (const r of rows) await new FirestoreCategoryRepository(db, UID).save(r) },
    merchants: (rows) => new FirestoreMerchantRepository(db, UID).saveMany(rows),
    rules: (rows) => new FirestoreRuleRepository(db, UID).saveMany(rows),
    people: async (rows) => { for (const r of rows) await new FirestorePersonRepository(db, UID).save(r) },
    assets: async (rows) => { for (const r of rows) await new FirestoreAssetRepository(db, UID).save(r) },
    tags: async (rows) => { for (const r of rows) await new FirestoreTagRepository(db, UID).save(r) },
    budgets: async (rows) => { for (const r of rows) await new FirestoreBudgetRepository(db, UID).save(r) },
    recurringItems: async (rows) => { for (const r of rows) await new FirestoreRecurringRepository(db, UID).save(r) },
    importBatches: async (rows) => { for (const r of rows) await new FirestoreImportBatchRepository(db, UID).save(r) },
    transactions: (rows) => new FirestoreTransactionRepository(db, UID).saveMany(rows),
    obligations: (rows) => new FirestoreObligationRepository(db, UID).saveMany(rows),
    allocations: (rows) => new FirestoreAllocationRepository(db, UID).saveMany(rows),
    settlements: (rows) => new FirestoreSettlementRepository(db, UID).saveMany(rows),
    sourceRecords: (rows) => new FirestoreSourceRecordRepository(db, UID).saveMany(rows),
    transactionTags: (rows) => new FirestoreTransactionTagRepository(db, UID).saveMany(rows),
    categoryBudgets: async (rows) => { for (const r of rows) await new FirestoreBudgetRepository(db, UID).saveCategoryBudget(r) },
    assetLots: (rows) => new FirestoreAssetLotRepository(db, UID).saveMany(rows),
    assetSales: (rows) => new FirestoreAssetSaleRepository(db, UID).saveMany(rows),
    assetPrices: async (rows) => { for (const r of rows) await new FirestoreAssetPriceRepository(db, UID).save(r) },
    notificationReceipts: (rows) => new FirestoreNotificationReceiptRepository(db, UID).saveMany(rows),
    projects: async (rows) => { for (const r of rows) await new FirestoreProjectRepository(db, UID).save(r) },
    projectLinks: (rows) => new FirestoreProjectLinkRepository(db, UID).saveMany(rows),
    projectRules: async (rows) => { for (const r of rows) await new FirestoreProjectRuleRepository(db, UID).save(r) },
  }
}

/** الحقل اللي بيعرّف الصف — زي `backupRowId`. **معرّف المستند نفسه** بيتقرا من المحاكي (مش بيتحسب هنا). */
function keyField(group: string): string {
  if (group === 'assetPrices') return 'assetId'
  if (group === 'notificationReceipts') return 'eventKey'
  if (group === 'budgets') return 'periodKey'
  return 'id'
}

type RawDoc = { name: string; fields?: Record<string, { stringValue?: string }> }

/** كل مستندات المجموعة خام: معرّف المستند زي ما اتخزن (مثلًا `eventKey` متشفّر) + الحقول بأنواعها. */
async function readGroup(group: string): Promise<RawDoc[]> {
  const url = `http://${HOST}/v1/projects/${PROJECT}/databases/(default)/documents/users/${UID}/${group}?pageSize=300`
  const res = await fetch(url, { headers: { Authorization: 'Bearer owner' } })
  if (!res.ok) throw new Error(`${group}: ${res.status} ${await res.text()}`)
  return ((await res.json()) as { documents?: RawDoc[] }).documents ?? []
}

async function main() {
  const app = initializeApp({ projectId: PROJECT, apiKey: 'fake-test-key', appId: 'docs-golden' }, 'docs-golden')
  const db = getFirestore(app)
  connectFirestoreEmulator(db, '127.0.0.1', 8088, { mockUserToken: { sub: UID } })
  const samples = documentSamples()
  const write = writers(db) as Record<string, (rows: unknown[]) => Promise<void>>
  const out: Record<string, { in: unknown; out: unknown }[]> = {}
  for (const [group, rows] of Object.entries(samples) as [string, Record<string, unknown>[]][]) {
    await write[group](rows)
    const stored = await readGroup(group)
    if (stored.length !== rows.length) throw new Error(`${group}: اتكتب ${rows.length} واتقرا ${stored.length}`)
    const key = keyField(group)
    out[group] = rows.map((row) => {
      const found = stored.find((d) => d.fields?.[key]?.stringValue === row[key])
      if (!found) throw new Error(`${group}: مفيش مستند ${key}=${String(row[key])}`)
      return { in: row, out: { id: found.name.split('/').pop(), fields: found.fields ?? {} } }
    })
  }
  await terminate(db)
  await deleteApp(app)
  const file = resolve(__dirname, '../../native-app/golden/documents.json')
  writeFileSync(file, JSON.stringify(out, null, 1) + '\n')
  console.log(`documents.json: ${Object.values(out).reduce((n, c) => n + c.length, 0)} مستند في ${Object.keys(out).length} مجموعة`)
}

await main()
