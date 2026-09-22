/**
 * بيولّد ملفات المرجع لتطبيق كوتلن من كود التطبيق الحالي — KOTLIN_PLAN §3.
 * التشغيل: `npm run golden` ⇒ `native-app/golden/*.json`.
 * لو سلوك اتغير في التطبيق الحالي: شغّله تاني، والفرق هيبان في git واختبارات كوتلن.
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { moneyGolden } from './moneyGolden'
import { normalizeGolden } from './normalizeGolden'
import { periodGolden } from './periodGolden'
import { ledgerGolden } from './ledgerGolden'
import { dedupeGolden } from './dedupeGolden'
import { categorizeGolden } from './categorizeGolden'
import { importGolden } from './importGolden'
import { smsGolden } from './smsGolden'
import { budgetGolden } from './budgetGolden'
import { categoriesGolden } from './categoriesGolden'
import { miscGolden } from './miscGolden'
import { investGolden } from './investGolden'
import { noticeGolden } from './noticeGolden'
import { backupGolden } from './backupGolden'
import { seedsGolden } from './seedsGolden'
import { pdfGolden } from './pdfGolden'
import { homeGolden } from './homeGolden'
import { transactionsGolden } from './transactionsGolden'
import { budgetScreenGolden } from './budgetScreenGolden'
import { importFlowGolden } from './importFlowGolden'
import { revertFlowGolden } from './revertFlowGolden'
import { txnEditGolden } from './txnEditGolden'
import { editDetailGolden } from './editDetailGolden'
import { peopleFlowGolden } from './peopleFlowGolden'

const OUT = resolve(__dirname, '../../native-app/golden')
const modules: Record<string, () => unknown | Promise<unknown>> = {
  money: moneyGolden,
  normalize: normalizeGolden,
  period: periodGolden,
  ledger: ledgerGolden,
  dedupe: dedupeGolden,
  categorize: categorizeGolden,
  import: importGolden,
  sms: smsGolden,
  budget: budgetGolden,
  categories: categoriesGolden,
  misc: miscGolden,
  invest: investGolden,
  notice: noticeGolden,
  backup: backupGolden,
  seeds: seedsGolden,
  pdf: pdfGolden,
  home: homeGolden,
  transactions: transactionsGolden,
  budgetScreen: budgetScreenGolden,
  importFlow: importFlowGolden,
  revertFlow: revertFlowGolden,
  txnEdit: txnEditGolden,
  editDetail: editDetailGolden,
  peopleFlow: peopleFlowGolden,
}

mkdirSync(OUT, { recursive: true })
for (const [name, build] of Object.entries(modules)) {
  const data = (await build()) as Record<string, unknown[]>
  // حالة في كل سطر: الملف أصغر، والفرق في git بيبان على الحالة اللي اتغيرت بس
  const body = Object.entries(data)
    .map(([fn, list]) => `${JSON.stringify(fn)}: [\n${list.map((c) => JSON.stringify(c)).join(',\n')}\n]`)
    .join(',\n')
  writeFileSync(resolve(OUT, `${name}.json`), `{\n${body}\n}\n`, 'utf8')
  const count = Object.values(data).reduce((n, list) => n + list.length, 0)
  console.log(`${name}.json — ${count} حالة`)
}
