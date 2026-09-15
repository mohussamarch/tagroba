import { Capacitor } from '@capacitor/core'
import type { RepairBackupPort } from '../application/ports/RepairBackupPort'
import { localFiles } from './localFiles'
import { saveTextFile, splitForWrite } from './saveTextFile'

/** يقطع النص على دفعات عبر جسر Capacitor — نفس دالة `saveTextFile` (مكتوبة مرة واحدة). */
export const splitForBridge = splitForWrite

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
