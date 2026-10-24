package app.masroufy.usecase

import app.masroufy.port.RepairBackupPort
import app.masroufy.port.SavedBackupFile

/**
 * r06-links2 (§118-ج): «النسخ المحفوظة على الجوال» في شاشة النسخة الاحتياطية — العرض والمسح بتأكيد. **ملفات ⇒ المسح مالوش تراجع**
 * (اللوحة بتقول كده). النسخة اللي المستخدم حفظها بنافذة «احفظ في» برّه البرنامج ⇒ مش هنا (البرنامج ما يشوفهاش).
 */
class ManageSavedBackups(private val port: RepairBackupPort) {
    suspend fun list(): List<SavedBackupFile> = port.list()

    /** بيمسح بس اسم موجود في القايمة (مش أي اسم جاي من برّه). */
    suspend fun delete(fileName: String): Boolean = port.list().any { it.name == fileName } && port.delete(fileName)
}
