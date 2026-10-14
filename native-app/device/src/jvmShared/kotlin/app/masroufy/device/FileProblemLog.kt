package app.masroufy.device

import app.masroufy.core.ProblemEntry
import app.masroufy.core.ProblemLogFormat
import app.masroufy.port.ProblemLogPort
import java.io.File

/**
 * «سجل المشاكل» في ملف خاص بالتطبيق (أندرويد: `filesDir/problems/problems.log` — مش بيتنسخ ولا بيتزامن، و`allowBackup=false`).
 * سطر لكل مشكلة بشكل [ProblemLogFormat.line] (كل حقل متنضف). الحلقة: بعد الإضافة لو عدّى [append]`.keepLast` بنكتب آخرهم بس
 * في ملف مؤقت وننقله مكان القديم (لو العملية ماتت في النص، القديم بيفضل سليم).
 * ⚠️ مشترك بين أندرويد والكمبيوتر (`src/jvmShared` — `java.io` بس) عشان يتختبر على الكمبيوتر بنفس الكود.
 */
class FileProblemLog(private val file: File) : ProblemLogPort {
    private val lock = Any()

    override fun append(entry: ProblemEntry, keepLast: Int) = synchronized(lock) {
        val lines = readLines() + ProblemLogFormat.line(entry)
        val keep = keepLast.coerceAtLeast(1)
        if (lines.size > keep) rewrite(lines.takeLast(keep))
        else {
            file.parentFile?.mkdirs()
            file.appendText(lines.last() + "\n", Charsets.UTF_8)
        }
    }

    override fun entries(): List<ProblemEntry> = synchronized(lock) { readLines().mapNotNull(ProblemLogFormat::parse) }

    override fun clear() = synchronized(lock) {
        if (file.exists()) file.delete()
        Unit
    }

    private fun readLines(): List<String> = if (file.isFile) file.readLines(Charsets.UTF_8).filter { it.isNotBlank() } else emptyList()

    private fun rewrite(lines: List<String>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
