package app.masroufy.firestore

import dev.gitlive.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * النسخة اللي على الجوال = نسخة مكتبة فايربيز نفسها (قرار المالك §54). الفئة دي بتخليها **كاملة**:
 * مستمع على كل مجموعة، وأول ما توصل نسخة **من السيرفر** (مش من النسخة المحلية) المجموعة بتتعلّم «اتزامنت».
 *
 * ⚠️ ليه الحرص ده: قراية الشاشة من النسخة المحلية بس، والنسخة ناقصة عمليات ما اتنزلتش، = مجموع **غلط من غير رسالة**
 * (القاعدة 10). عشان كده المستودعات بتقرا من النسخة المحلية **للمجموعات اللي اتزامنت بس** ([sourceFor])،
 * وغيرها بالطريقة العادية (السيرفر لو فيه نت). المستمع شغال ⇒ أي تعديل من جهاز تاني بيوصل النسخة المحلية لوحده.
 */
class FirestoreSync(private val space: FirestoreSpace, private val groups: List<String>) {
    private val synced = MutableStateFlow<Set<String>>(emptySet())
    private val jobs = mutableListOf<Job>()

    /** المجموعات اللي اتزامنت — للشاشة تعرض شريط تقدم أول مرة. */
    val syncedGroups: StateFlow<Set<String>> = synced.asStateFlow()

    val total: Int get() = groups.size

    fun start(scope: CoroutineScope) {
        if (jobs.isNotEmpty()) return
        space.sourceFor = { group -> if (group in synced.value) Source.CACHE else Source.DEFAULT }
        // من غير نت الكتابة بتروح النسخة المحلية وما بنستناش السيرفر (§54) — والفشل بييجي في `space.writeFailures`
        space.writeScope = scope
        for (group in groups) {
            jobs += scope.launch {
                space.collection(group).snapshots(includeMetadataChanges = true).collect { snap ->
                    if (!snap.metadata.isFromCache) synced.update { it + group }
                }
            }
        }
    }

    /** لحد ما كل المجموعات تتزامن (التنزيل الأول) — الشاشة بتستنى ده بشريط تقدم. */
    suspend fun awaitComplete() {
        synced.first { it.containsAll(groups) }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        synced.value = emptySet()
        space.sourceFor = { Source.DEFAULT }
        space.writeScope = null
    }
}
