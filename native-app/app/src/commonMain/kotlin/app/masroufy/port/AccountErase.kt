package app.masroufy.port

/**
 * r06-links2 (§118-ج «امسح حسابي»): مسح **كل** بيانات المستخدم على السيرفر تحت `users/{uid}` (البلد الأساسي والبلاد التانية وملف
 * الحساب). بيرجّع عدد المستندات اللي اتمسحت. التنفيذ في `:firestore`؛ الاختبارات ببديل في الذاكرة بس (ممنوع المشروع الحقيقي).
 */
interface AccountDataEraser {
    suspend fun eraseAll(uid: String): Int
}
