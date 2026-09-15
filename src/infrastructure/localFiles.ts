import { registerPlugin } from '@capacitor/core'

/** إضافة الملفات الأصلية (LocalFilesPlugin.java) — تسجيل واحد يتشارك بين المحوّلات. */
export const localFiles = registerPlugin<{
  /**
   * نافذة حفظ النظام: المستخدم يختار المكان. بترمي التطبيق للخلفية.
   * المحتوى **لازم يتكتب الأول** في ملف مؤقت بـ`writeAppFile` واسمه يتبعت هنا — المحتوى الكبير جوه
   * النداء نفسه كان بيوقّع التطبيق (`TransactionTooLargeException`، 2026-09-15). الإضافة بتمسح المؤقت بعدها.
   */
  save(options: { sourceName: string; filename: string; mimeType: string }): Promise<void>
  /** كتابة في مجلد التطبيق من غير نافذة. */
  writeAppFile(options: { name: string; content: string; append: boolean }): Promise<{ location: string }>
  /** حجم الملف على القرص بعد الكتابة. */
  appFileSize(options: { name: string }): Promise<{ bytes: number; location: string }>
}>('LocalFiles')
