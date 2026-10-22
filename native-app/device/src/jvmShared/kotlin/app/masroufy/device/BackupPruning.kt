package app.masroufy.device

/** ملف نسخة احتياطية على الجهاز: اسمه وآخر تعديل (بالملّي ثانية). */
data class BackupFileInfo(val name: String, val modifiedMillis: Long)

/**
 * النسخ الاحتياطية الداخلية (قبل الإصلاح): **آخر [KEEP] ملف بس** — كل ما تتعمل نسخة جديدة الزيادة بتتمسح (قرار المالك 2026-10-22).
 * بترجّع أسامي اللي يتمسح. الملف اللي لسه اتكتب ([justWritten]) عمره ما بيتمسح حتى لو ساعة الجهاز رجعت لورا؛ الترتيب بآخر تعديل
 * (الأحدث الأول) وعند التساوي بالاسم (أسامي النسخ فيها التاريخ والوقت).
 */
object BackupPruning {
    const val KEEP = 2

    fun toDelete(files: List<BackupFileInfo>, justWritten: String, keep: Int = KEEP): List<String> {
        val ordered = files.filter { it.name != justWritten }.sortedWith(compareByDescending<BackupFileInfo> { it.modifiedMillis }.thenByDescending { it.name })
        // الملف الجديد واخد خانة من الـ[keep]
        return ordered.drop((keep - 1).coerceAtLeast(0)).map { it.name }
    }
}
