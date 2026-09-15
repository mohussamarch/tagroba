import { Capacitor } from '@capacitor/core'
import { localFiles } from './localFiles'

/** حجم الدفعة الواحدة وهي بتتكتب في الملف المؤقت — أصغر بكتير من حدود جسر Capacitor. */
const CHUNK = 256_000
/** اسم الملف المؤقت جوه مجلد التطبيق — `LocalFilesPlugin` بيمسحه بعد الحفظ أو الإلغاء. */
const PENDING_NAME = 'export-pending.tmp'

/** يقسم النص من غير ما يقطع حرف من حرفين (إيموجي مثلًا) في النص. */
export function splitForWrite(content: string, size = CHUNK): string[] {
  const parts: string[] = []
  let start = 0
  while (start < content.length) {
    let end = Math.min(start + size, content.length)
    const code = content.charCodeAt(end - 1)
    if (end < content.length && code >= 0xd800 && code <= 0xdbff) end -= 1
    parts.push(content.slice(start, end))
    start = end
  }
  return parts.length ? parts : ['']
}

export async function saveTextFile(content: string, filename: string, mimeType: string): Promise<void> {
  if (Capacitor.isNativePlatform()) {
    // المحتوى يتكتب على دفعات في ملف مؤقت، والنافذة بتاخد اسمه بس (LocalFilesPlugin.save)
    const parts = splitForWrite(content)
    for (let i = 0; i < parts.length; i++) {
      await localFiles.writeAppFile({ name: PENDING_NAME, content: parts[i], append: i > 0 })
    }
    await localFiles.save({ sourceName: PENDING_NAME, filename, mimeType: mimeType.split(';')[0] })
    return
  }
  const url = URL.createObjectURL(new Blob([content], {type: mimeType}))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
