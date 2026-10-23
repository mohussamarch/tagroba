package app.masroufy.device

import app.masroufy.core.SupportKind
import app.masroufy.core.SupportMessage
import app.masroufy.port.SupportOutboxPort
import java.io.File

/**
 * صندوق «ابعتلنا» في ملف خاص بالتطبيق (أندرويد: `filesDir/support/outbox.log` — مش بيتزامن ومش بيتبعت لأي مكان).
 * سطر لكل رسالة: `النوع<TAB>اليوم<TAB>البلد<TAB>النص` — فواصل السطر والتبويب جوه النص بتتحول (`\n` ⇒ `\\n` · TAB ⇒ مسافة).
 * ⚠️ مشترك بين أندرويد والكمبيوتر (`src/jvmShared` — `java.io` بس) عشان يتختبر بنفس الكود.
 */
class FileSupportOutbox(private val file: File) : SupportOutboxPort {
    private val lock = Any()

    override fun add(message: SupportMessage) = synchronized(lock) {
        file.parentFile?.mkdirs()
        file.appendText(line(message) + "\n", Charsets.UTF_8)
    }

    override fun all(): List<SupportMessage> = synchronized(lock) {
        if (!file.isFile) emptyList() else file.readLines(Charsets.UTF_8).mapNotNull(::parse)
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
