import { Capacitor, registerPlugin } from '@capacitor/core'

const files = registerPlugin<{ save(options: {content: string; filename: string; mimeType: string}): Promise<void> }>('LocalFiles')
export async function saveTextFile(content: string, filename: string, mimeType: string): Promise<void> {
  if (Capacitor.isNativePlatform()) {
    await files.save({content, filename, mimeType: mimeType.split(';')[0]})
    return
  }
  const url = URL.createObjectURL(new Blob([content], {type: mimeType}))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
