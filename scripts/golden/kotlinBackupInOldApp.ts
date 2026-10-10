/**
 * نسخة شاملة عملها التطبيق الجديد **وفيها «المستحقات»** ⇐ التطبيق الحالي بيعمل بيها إيه؟ (OVERRIDES §55)
 * الملفين بيطلعوا من `DuesBackupTest` في كوتلن (`native-app/data/build/`، بيانات وهمية):
 *  1. `kotlin-dues-backup.json` — قسط الجمعية بنوعه الجديد (`rosca_contribution`) ⇒ **التطبيق الحالي بيرفض الملف كله برسالة** (مش بيضيّع حاجة في صمت).
 *  2. `kotlin-dues-backup-old-kinds.json` — نفس الحساب بنوع عملية يعرفه ⇒ بيقبل الملف ويرجّع الـ24 مجموعة ويتجاهل «المستحقات».
 * و«حساب لكل بلد» (OVERRIDES §64 — من `SpacesBackupTest`):
 *  3. `kotlin-spaces-backup-v3.json` — حساب فيه مصر ⇒ الإصدار 3 ⇒ **التطبيق الحالي بيرفضه برسالة الإصدار** (مش بيرجّع نصه في صمت).
 *  4. `kotlin-spaces-backup-v2.json` — نفس الحساب من غير بلد تانية ⇒ الإصدار 2 ⇒ بيقبله.
 * وحقول الشريحة S3 على العملية (§75-6 · §75-12 · §77-D — من `TransactionNewFieldsTest`):
 *  5. `kotlin-s3-fields-backup.json` — `suggestedKind` · `reversalOfId`/`reversedById` · `foreignCurrency`/`foreignAmountMinor` ⇒ بيقبله.
 *  6. `kotlin-s3-kind-before-backup.json` — زوج اتلغى بإيد المالك ومعاه `kindBeforeReversal` ⇒ بيقبله.
 *  7. `kotlin-s3-confirmed-refund-backup.json` — «استرداد» **مؤكد** (`refund_received` — نوع §42 مش عنده) ⇒ بيرفضه برسالة (زي المستحقات §55).
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
const spacesV3 = await tryRestore('kotlin-spaces-backup-v3.json')
const spacesV2 = await tryRestore('kotlin-spaces-backup-v2.json')
const s3Fields = await tryRestore('kotlin-s3-fields-backup.json')
const s3KindBefore = await tryRestore('kotlin-s3-kind-before-backup.json')
const s3ConfirmedRefund = await tryRestore('kotlin-s3-confirmed-refund-backup.json')
console.log(JSON.stringify([realistic, oldKinds, spacesV3, spacesV2, s3Fields, s3KindBefore, s3ConfirmedRefund]))
const ok = !realistic.accepted && realistic.message === 'نوع اقتصادي غير صالح' && oldKinds.accepted && oldKinds.totalAdded === 3 && oldKinds.duesKept?.length === 0 &&
  !spacesV3.accepted && spacesV3.message === 'اختر نسخة شاملة بإصدار 2؛ للنسخ القديمة استخدم استعادة النسخة القديمة' &&
  spacesV2.accepted && spacesV2.totalAdded === 6 &&
  s3Fields.accepted && s3Fields.totalAdded === 4 && s3KindBefore.accepted && s3KindBefore.totalAdded === 2 &&
  !s3ConfirmedRefund.accepted && s3ConfirmedRefund.message === 'نوع اقتصادي غير صالح'
process.exit(ok ? 0 : 1)
