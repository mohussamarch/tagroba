package app.masroufy.firestore

import app.masroufy.perf.PerfTrace
import dev.gitlive.firebase.firestore.ChangeType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** المستمعين شغالين؟ — بعد الخروج لازم يبقى `false` (`AccountSession`). */
    val isRunning: Boolean get() = jobs.any { it.isActive }

    /** عدد المستندات اللي في الذاكرة — بعد الخروج لازم يبقى صفر (بيانات الحساب ما تفضلش في الذاكرة). */
    val documentsInMemory: Int get() = mirror.documentCount()

    private val failures = MutableSharedFlow<Throwable>(replay = 1, extraBufferCapacity = 16)

    /**
     * مستمع وقع (صلاحيات بعد الخروج مثلًا). المجموعة بتتشال من «اتزامنت» — الذاكرة ما بقتش بتتحدّث، فالقراية بترجع لفايربيز
     * بدل ما تقرا نسخة قديمة من غير رسالة.
     * ⚠️ من غير الالتقاط ده، خطأ المستمع كان هيوقّع التطبيق كله.
     */
    val errors: SharedFlow<Throwable> = failures.asSharedFlow()

    private val remote = MutableStateFlow(0L)

    /**
     * بيزيد لما نسخة من **السيرفر** تغيّر الذاكرة (مجموعة لحقت بالسيرفر بعد الفتح من نسخة الجهاز، أو تعديل من جهاز تاني/التطبيق القديم)
     * ⇒ الشاشة اللي بتعرض أرقام تقرا تاني (`AppDeps.remoteChanges`). كتابة التطبيق نفسه لسه معلّقة ما بتزودوش.
     */
    val remoteChanges: StateFlow<Long> = remote.asStateFlow()

    /** الجهاز ده خلّص تنزيل كامل قبل كده ⇒ القراية من نسخة الجهاز لحد ما السيرفر يرد ([LocalMirror.deviceCopyComplete]). */
    fun trustDeviceCopy() {
        mirror.deviceCopyComplete = true
    }

    fun start(scope: CoroutineScope) {
        if (jobs.isNotEmpty()) return
        space.mirror = mirror
        // من غير نت الكتابة بتروح النسخة المحلية وما بنستناش السيرفر (§54) — والفشل بييجي في `space.writeFailures`
        space.writeScope = scope
        for (group in groups) {
            jobs += scope.launch {
                val start = PerfTrace.mark()
                try {
                    space.collection(group).snapshots(includeMetadataChanges = true).collect { snap ->
                        val changes = snap.documentChanges.map { change ->
                            change.document.id to if (change.type == ChangeType.REMOVED) null else change.document.rawData()
                        }
                        mirror.applyChanges(group, changes)
                        mirror.markDelivered(group)
                        if (PerfTrace.enabled && changes.isNotEmpty()) {
                            ReadMeter.server("listen:$group", changes.map { it.second }, snap.metadata.isFromCache, start)
                        }
                        val fromServer = !snap.metadata.isFromCache
                        if (fromServer && !mirror.isSynced(group)) {
                            mirror.markSynced(group)
                            PerfTrace.log("synced group=$group ms=${start.elapsedNow().inWholeMilliseconds}")
                            remote.update { it + 1 }
                        } else if (fromServer && changes.isNotEmpty() && !snap.metadata.hasPendingWrites) {
                            remote.update { it + 1 }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    mirror.markStale(group)
                    failures.tryEmit(e)
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
