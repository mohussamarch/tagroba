import { Capacitor } from '@capacitor/core'
import type { RepairBackupPort } from '../application/ports/RepairBackupPort'
import { localFiles } from './localFiles'
import { saveTextFile } from './saveTextFile'

/** حجم القطعة الواحدة عبر جسر Capacitor — رسالة الجسر نص JSON، والملف ممكن يبقى ميجات. */
const CHUNK = 256_000

/** يقطع النص من غير ما يفصل زوج بدائل UTF-16 (رمز تعبيري مثلًا) بين قطعتين. */
export function splitForBridge(content: string, size = CHUNK): string[] {
  const parts: string[] = []
  let start = 0
  while (start < content.length) {
    let end = Math.min(start + size, content.length)
    const code = content.charCodeAt(end - 1)
    if (end < content.length && code >= 0xd800 && code <= 0xdbff) end--
    parts.push(content.slice(start, end))
    start = end
  }
  return parts.length ? parts : ['']
}

/**
 * على أندرويد: مجلد التطبيق (`Android/data/app.masroufy.personal/files/backups`) من غير
 * نافذة، وبعدها الحجم بيتقرا من القرص. على المتصفح: تنزيل عادي، والحجم ما يتأكدش.
 */
export const deviceRepairBackup: RepairBackupPort = {
  async save(fileName, content) {
    if (!Capacitor.isNativePlatform()) {
      await saveTextFile(content, fileName, 'application/json')
      return { location: 'تنزيلات المتصفح', bytes: null }
    }
    const parts = splitForBridge(content)
    for (let i = 0; i < parts.length; i++) {
      await localFiles.writeAppFile({ name: fileName, content: parts[i], append: i > 0 })
    }
    const { bytes, location } = await localFiles.appFileSize({ name: fileName })
    return { location, bytes }
  },
}
