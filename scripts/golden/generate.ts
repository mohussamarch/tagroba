/**
 * بيولّد ملفات المرجع لتطبيق كوتلن من كود التطبيق الحالي — KOTLIN_PLAN §3.
 * التشغيل: `npm run golden` ⇒ `native-app/golden/*.json`.
 * لو سلوك اتغير في التطبيق الحالي: شغّله تاني، والفرق هيبان في git واختبارات كوتلن.
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { moneyGolden } from './moneyGolden'
import { normalizeGolden } from './normalizeGolden'

const OUT = resolve(__dirname, '../../native-app/golden')
const modules: Record<string, () => unknown> = {
  money: moneyGolden,
  normalize: normalizeGolden,
}

mkdirSync(OUT, { recursive: true })
for (const [name, build] of Object.entries(modules)) {
  const data = build()
  writeFileSync(resolve(OUT, `${name}.json`), JSON.stringify(data, null, 1) + '\n', 'utf8')
  const count = Object.values(data as Record<string, unknown[]>).reduce((n, list) => n + list.length, 0)
  console.log(`${name}.json — ${count} حالة`)
}
