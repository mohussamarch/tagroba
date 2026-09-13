import { registerPlugin } from '@capacitor/core'

/** إضافة الملفات الأصلية (LocalFilesPlugin.java) — تسجيل واحد يتشارك بين المحوّلات. */
export const localFiles = registerPlugin<{
  /** نافذة حفظ النظام: المستخدم يختار المكان. بترمي التطبيق للخلفية. */
  save(options: { content: string; filename: string; mimeType: string }): Promise<void>
  /** كتابة في مجلد التطبيق من غير نافذة. */
  writeAppFile(options: { name: string; content: string; append: boolean }): Promise<{ location: string }>
  /** حجم الملف على القرص بعد الكتابة. */
  appFileSize(options: { name: string }): Promise<{ bytes: number; location: string }>
}>('LocalFiles')
