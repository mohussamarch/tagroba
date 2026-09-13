import type { RepairBackupPort } from '../../application/ports/RepairBackupPort'

/** تخزين نسخ ما قبل الإصلاح في الذاكرة للاختبار. `truncate` يحاكي كتابة ناقصة. */
export function memoryRepairBackup(options: { truncate?: boolean } = {}): RepairBackupPort & { files: Map<string, string> } {
  const files = new Map<string, string>()
  return {
    files,
    async save(fileName, content) {
      const stored = options.truncate ? content.slice(0, Math.floor(content.length / 2)) : content
      files.set(fileName, stored)
      return { location: `memory:${fileName}`, bytes: new TextEncoder().encode(stored).length }
    },
  }
}
