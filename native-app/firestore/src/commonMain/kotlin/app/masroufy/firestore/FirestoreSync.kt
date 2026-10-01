package app.masroufy.firestore

import dev.gitlive.firebase.firestore.ChangeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * النسخة اللي على الجوال = نسخة مكتبة فايربيز نفسها (قرار المالك §54). الفئة دي بتخليها **كاملة** وسريعة:
 * مستمع على كل مجموعة بيملى [LocalMirror] (في الذاكرة)، وأول ما توصل نسخة **من السيرفر** (مش من النسخة المحلية)
 * المجموعة بتتعلّم «اتزامنت» والمستودعات بتقرا منها من الذاكرة.
 *
 * ⚠️ ليه الحرص ده: قراية الشاشة من نسخة ناقصة عمليات ما اتنزلتش = مجموع **غلط من غير رسالة** (القاعدة 10).
 * المجموعة اللي ما اتزامنتش بتتقرا بالطريقة العادية (السيرفر لو فيه نت). المستمع شغال ⇒ أي تعديل من جهاز تاني بيوصل لوحده.
 */
class FirestoreSync(private val space: FirestoreSpace, private val groups: List<String>) {
    private val jobs = mutableListOf<Job>()
    private val mirror = LocalMirror(groups)

    /** المجموعات اللي اتزامنت — للشاشة تعرض شريط تقدم أول مرة. */
    val syncedGroups: StateFlow<Set<String>> = mirror.syncedGroups

    val total: Int get() = groups.size

    fun start(scope: CoroutineScope) {
        if (jobs.isNotEmpty()) return
        space.mirror = mirror
        // من غير نت الكتابة بتروح النسخة المحلية وما بنستناش السيرفر (§54) — والفشل بييجي في `space.writeFailures`
        space.writeScope = scope
        for (group in groups) {
            jobs += scope.launch {
                space.collection(group).snapshots(includeMetadataChanges = true).collect { snap ->
                    mirror.applyChanges(group, snap.documentChanges.map { change ->
                        change.document.id to if (change.type == ChangeType.REMOVED) null else change.document.rawData()
                    })
                    if (!snap.metadata.isFromCache) mirror.markSynced(group)
                }
            }
        }
    }

    /** لحد ما كل المجموعات تتزامن (التنزيل الأول) — الشاشة بتستنى ده بشريط تقدم. */
    suspend fun awaitComplete() {
        syncedGroups.first { it.containsAll(groups) }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        mirror.clear()
        space.mirror = null
        space.writeScope = null
    }
}
