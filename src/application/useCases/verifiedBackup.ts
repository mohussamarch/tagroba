import type { Clock } from '../ports/repositories'
import type { RepairBackupPort, SavedBackup } from '../ports/RepairBackupPort'

export type VerifiedBackup = SavedBackup & { fileName: string; expectedBytes: number }

/**
 * نسخة قبل أي كتابة صيانة (إصلاح، تنظيف، تراجع) — HANDOVER §36.
 *
 * بتتحفظ من غير نافذة (RepairBackupPort)، والحجم اللي اتقرا من القرص لازم يساوي عدد
 * بايتات UTF-8 المتوقع **بالظبط**، وإلا بترمي خطأ قبل ما حالة الاستخدام تلمس أي مستند.
 * `bytes === null` = تنزيل متصفح ما يتأكدش حجمه، ويتقال ده للمستخدم.
 */
export async function saveVerifiedBackup(
  backup: RepairBackupPort,
  clock: Clock,
  kind: string,
  documents: unknown[],
): Promise<VerifiedBackup> {
  const savedAt = clock.nowIso()
  const content = JSON.stringify({ app: 'masroufy', kind, savedAt, documents }, null, 2)
  const fileName = `masroufy-${kind}-${savedAt.replace(/[:.]/g, '-')}.json`
  const expectedBytes = new TextEncoder().encode(content).length
  const saved = await backup.save(fileName, content)
  if (saved.bytes !== null && saved.bytes !== expectedBytes) {
    throw new Error(`النسخة الاحتياطية اتحفظت ناقصة (${saved.bytes} من ${expectedBytes} بايت) — ما اتلمسش ولا مستند`)
  }
  return { ...saved, fileName, expectedBytes }
}
