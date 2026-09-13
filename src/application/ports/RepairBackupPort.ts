/**
 * حفظ نسخة «قبل الإصلاح» **من غير نافذة نظام** — HANDOVER «إصلاح البيانات القديمة اتقطع».
 * نافذة حفظ الملف على أندرويد بترمي التطبيق للخلفية وتقطع الإصلاح قبل ما يبدأ.
 */
export interface SavedBackup {
  /** مكان الملف بلغة يفهمها المستخدم. */
  location: string
  /**
   * الحجم الفعلي بالبايت كما قرأه التخزين **بعد** الكتابة.
   * null = التخزين ما يقدرش يأكد (تنزيل المتصفح) — ويتقال كده للمستخدم.
   */
  bytes: number | null
}

export interface RepairBackupPort {
  save(fileName: string, content: string): Promise<SavedBackup>
}
