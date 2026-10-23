package app.masroufy.device

import app.masroufy.core.SupportKind
import app.masroufy.core.SupportMessage
import app.masroufy.port.PendingSupport
import app.masroufy.port.SupportOutboxPort
import java.io.File

/**
 * صندوق «ابعتلنا» في ملفات خاصة بالتطبيق (أندرويد: `filesDir/support/` — مش بيتزامن):
 * - `outbox.log` = النسخة المحلية كلها: سطر لكل رسالة `النوع<TAB>اليوم<TAB>البلد<TAB>النص` — فواصل السطر والتبويب جوه النص بتتحول
 *   (`\n` ⇒ `\\n` · TAB ⇒ مسافة).
 * - `pending.log` (جنبه) = اللي لسه ما اتبعتش لفايربيز: `المعرّف<TAB>` + نفس السطر. بيتكتب من الأول لما رسالة تتبعت ([sent]).
 * ⚠️ مشترك بين أندرويد والكمبيوتر (`src/jvmShared` — `java.io` بس) عشان يتختبر بنفس الكود.
 */
class FileSupportOutbox(private val file: File) : SupportOutboxPort {
    private val lock = Any()
    private val pendingFile = File(file.parentFile, "pending.log")

    override fun add(message: SupportMessage, id: String) = synchronized(lock) {
        file.parentFile?.mkdirs()
        file.appendText(line(message) + "\n", Charsets.UTF_8)
        pendingFile.appendText(id.replace('\t', ' ').replace("\n", "") + "\t" + line(message) + "\n", Charsets.UTF_8)
    }

    override fun all(): List<SupportMessage> = synchronized(lock) {
        if (!file.isFile) emptyList() else file.readLines(Charsets.UTF_8).mapNotNull(::parse)
    }

    override fun pending(): List<PendingSupport> = synchronized(lock) { readPending() }

    override fun sent(id: String) = synchronized(lock) {
        val left = readPending().filterNot { it.id == id }
        if (left.isEmpty()) pendingFile.delete() else pendingFile.writeText(left.joinToString("") { it.id + "\t" + line(it.message) + "\n" }, Charsets.UTF_8)
        Unit
    }

    private fun readPending(): List<PendingSupport> = if (!pendingFile.isFile) emptyList() else pendingFile.readLines(Charsets.UTF_8).mapNotNull { l ->
        val tab = l.indexOf('\t')
        if (tab <= 0) null else parse(l.substring(tab + 1))?.let { PendingSupport(l.substring(0, tab), it) }
    }

    companion object {
        internal fun line(m: SupportMessage): String =
            listOf(m.kind.name, m.day, m.countryCode.orEmpty(), m.text.replace("\\", "\\\\").replace("\r", "").replace("\n", "\\n").replace('\t', ' ')).joinToString("\t")

        internal fun parse(line: String): SupportMessage? {
            val p = line.split('\t', limit = 4)
            if (p.size < 4) return null
            val kind = SupportKind.entries.firstOrNull { it.name == p[0] } ?: return null
            val text = buildString {
                var i = 0
                val s = p[3]
                while (i < s.length) {
                    val c = s[i]
                    if (c == '\\' && i + 1 < s.length) {
                        append(if (s[i + 1] == 'n') '\n' else s[i + 1])
                        i += 2
                    } else {
                        append(c)
                        i++
                    }
                }
            }
            return SupportMessage(kind, text, p[1], p[2].ifEmpty { null })
        }
    }
}
