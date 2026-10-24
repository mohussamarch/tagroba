package app.masroufy.firestore

import app.masroufy.core.SPACES_GROUP
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * r06-fix055b (BLOCKER «امسح حسابي»): حارس إن [FirestoreAccountEraser] بيغطي **كل** مجموعة تحت `users/{uid}`.
 * بيمسح كود موديول firestore: كل `collection(…)`/`document(…)` بمسار نصي أو ثابت ⇒ اسم المجموعة لازم يبقى في [USER_COLLECTIONS]
 * (أو [SHARED_COLLECTIONS] برا المستخدم). وكمان كل محوّل `codec("…")` في `data` وكل `const val …_GROUP` في core/data/firestore.
 * المجموعات اللي اسمها متغير (`codec.group`) بتيجي من المحوّلات — والمحوّلات نفسها متغطية بالفحص التاني.
 */
class UserCollectionsGuardTest {
    private fun root(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return dir ?: fail("ما لقيناش جذر المشروع (settings.gradle.kts)")
    }

    private fun sources(module: String): List<File> =
        File(root(), "$module/src").walkTopDown().filter { it.isFile && it.extension == "kt" && !it.path.contains("Test") }.toList()

    /** اسم الثابت ⇒ قيمه (`COLLECTION` موجود مرتين: sharedMerchants و inbox). */
    private val constants: Map<String, List<String>> by lazy {
        val decl = Regex("""const val ([A-Z][A-Z0-9_]*)\s*=\s*"([^"$]*)"""")
        listOf("core", "data", "firestore").flatMap(::sources)
            .flatMap { f -> decl.findAll(f.readText()).map { it.groupValues[1] to it.groupValues[2] }.toList() }
            .groupBy({ it.first }, { it.second })
    }

    private val covered: Set<String> get() = (USER_COLLECTIONS + SHARED_COLLECTIONS + SPACES_GROUP + "users").toSet()

    /** أسامي المجموعات في مسار نصي: بعد `${space.root}`/`$root` (مستند) أو من الأول (`users/…`) كل جزء في خانة زوجية مجموعة. */
    private fun groupsInPath(path: String): List<String> {
        val parts = path.split('/').filter { it.isNotEmpty() }
        val offset = if (parts.firstOrNull()?.contains("root") == true) 1 else 0
        return parts.drop(offset).filterIndexed { i, _ -> i % 2 == 0 }.flatMap(::resolve)
    }

    /** `$x` و`${x}` ⇒ قيمة الثابت لو `x` ثابت معروف (متغير زي `$uid` أو `$group` ⇒ ولا حاجة). */
    private fun resolve(token: String): List<String> =
        if (token.startsWith("$")) constants[token.removePrefix("$").removePrefix("{").removeSuffix("}")].orEmpty() else listOf(token)

    private fun scanFirestoreModule(): List<Pair<String, String>> {
        val call = Regex("""\b(collection|document)\(\s*(?:"([^"]*)"|([A-Z][A-Z0-9_]*)|[A-Za-z]+\.([A-Z][A-Z0-9_]*))\s*\)""")
        val found = mutableListOf<Pair<String, String>>()
        for (file in sources("firestore")) {
            for (m in call.findAll(file.readText())) {
                val kind = m.groupValues[1]
                val literal = m.groups[2]?.value
                val ident = m.groups[3]?.value ?: m.groups[4]?.value
                val names = when {
                    literal != null -> groupsInPath(literal)
                    ident != null && kind == "collection" -> constants[ident] ?: listOf("?$ident")
                    else -> emptyList()
                }
                for (n in names) found += "${file.name}: ${m.value}" to n
            }
        }
        return found
    }

    @Test fun everyCollectionPathInFirestoreModuleIsErased() {
        val found = scanFirestoreModule()
        val names = found.map { it.second }.toSet()
        // الماسح نفسه شغال: لازم يلاقي المستندات المفردة اللي فاتت المسح قبل كده
        for (expected in listOf("initialization", "concurrency", "profile", "sharedMerchants", "inbox")) {
            assertTrue(expected in names, "الماسح ما لقاش `$expected` — الـregex بايظ: $names")
        }
        val missing = found.filter { it.second !in covered }
        assertTrue(missing.isEmpty(), "مجموعات تحت المستخدم مش في USER_COLLECTIONS (هتفضل بعد «امسح حسابي»): $missing")
    }

    @Test fun everyCodecGroupIsErased() {
        val codec = Regex("""\bcodec\(\s*(?:"([A-Za-z0-9_]+)"|([A-Z][A-Z0-9_]*))\s*,""")
        val groups = sources("data").flatMap { f ->
            codec.findAll(f.readText()).flatMap { m ->
                if (m.groupValues[1].isNotEmpty()) listOf(m.groupValues[1]) else constants[m.groupValues[2]] ?: listOf("?${m.groupValues[2]}")
            }.toList()
        }
        assertTrue(groups.size > 40, "الماسح لقى ${groups.size} محوّل بس")
        val missing = groups.filter { it !in covered }
        assertTrue(missing.isEmpty(), "محوّلات مجموعتها مش في USER_COLLECTIONS: $missing")
    }

    @Test fun everyGroupConstantIsErased() {
        val groups = constants.filterKeys { it.endsWith("_GROUP") }
        assertTrue("ALERT_DISMISSALS_GROUP" in groups && "PROFILE_GROUP" in groups && "INITIALIZATION_GROUP" in groups, "الماسح ما لقاش الثوابت: ${groups.keys}")
        val missing = groups.filterValues { values -> values.any { it !in covered } }
        assertTrue(missing.isEmpty(), "ثوابت مجموعات مش في USER_COLLECTIONS: $missing")
    }

    @Test fun sharedCollectionsStayOutsideTheUser() {
        assertTrue(SHARED_COLLECTIONS.none { it in USER_COLLECTIONS }, "مجموعة مشتركة اتحطت في مسح المستخدم")
        assertTrue("eg" in KNOWN_SPACE_IDS, "مصر لازم تبقى في البلاد المعروفة: $KNOWN_SPACE_IDS")
    }
}
