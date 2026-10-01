/**
 * نسخة شاملة عملها التطبيق الجديد **وفيها «المستحقات»** ⇐ التطبيق الحالي بيعمل بيها إيه؟ (OVERRIDES §55)
 * الملفين بيطلعوا من `DuesBackupTest` في كوتلن (`native-app/data/build/`، بيانات وهمية):
 *  1. `kotlin-dues-backup.json` — قسط الجمعية بنوعه الجديد (`rosca_contribution`) ⇒ **التطبيق الحالي بيرفض الملف كله برسالة** (مش بيضيّع حاجة في صمت).
 *  2. `kotlin-dues-backup-old-kinds.json` — نفس الحساب بنوع عملية يعرفه ⇒ بيقبل الملف ويرجّع الـ24 مجموعة ويتجاهل «المستحقات».
 * التشغيل (PowerShell، من جذر المشروع، بعد `./gradlew :data:jvmTest` في `native-app`):
 *   & E:\work\masroufy\node_modules\.bin\vite-node.cmd scripts/golden/kotlinBackupInOldApp.ts
 */
import { readFileSync } from 'node:fs'
import { makeFullBackup } from '../../src/application/useCases/fullBackup'
import { memoryFullBackup } from '../../src/infrastructure/memory/fullBackup'
import { backupDigest } from '../../src/infrastructure/backupDigest'

const DUES = ['roscas', 'roscaEntries', 'installmentPlans', 'installmentPayments', 'debtTerms']

async function tryRestore(name: string) {
  const port = memoryFullBackup()
  const backup = makeFullBackup(port, backupDigest)
  try {
    const plan = await backup.plan(readFileSync(`native-app/data/build/${name}`, 'utf8'))
    const outcome = await backup.apply(plan.file)
    const stored = await port.read()
    return { name, accepted: true, totalAdded: outcome.totalAdded, duesKept: DUES.filter(g => g in stored) }
  } catch (e) {
    return { name, accepted: false, message: (e as Error).message }
  }
}

const realistic = await tryRestore('kotlin-dues-backup.json')
const oldKinds = await tryRestore('kotlin-dues-backup-old-kinds.json')
console.log(JSON.stringify([realistic, oldKinds]))
const ok = !realistic.accepted && realistic.message === 'نوع اقتصادي غير صالح' && oldKinds.accepted && oldKinds.totalAdded === 3 && oldKinds.duesKept?.length === 0
process.exit(ok ? 0 : 1)
