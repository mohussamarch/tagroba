package app.masroufy.ui.nav

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * مفتاح الخانة اللي الشاشة دي جوّاها في السجل (`AppShell` بيديه لكل شاشة: `s<id>` · `tab-<TAB>`). برّه السجل (لوحة · معاينة) = null
 * ⇒ التمرير عادي من غير حفظ.
 */
val LocalEntryKey = compositionLocalOf<String?> { null }

/**
 * قايمة (`LazyColumn`) **بترجع لمكانها** مع الرجوع لخانتها (OVERRIDES §85) — `TabScaffold`/`InnerScaffold` بيستعملوها، فكل الشاشات
 * اللي عليهم بتاخدها من غير ما تعمل حاجة. [slot] لو في الشاشة أكتر من قايمة.
 */
@Composable
fun rememberEntryLazyListState(slot: String = "main"): LazyListState {
    val nav = LocalNavigator.current
    val entry = LocalEntryKey.current
    val saved = remember(entry, slot) { entry?.let { nav.scrolls.list(it, slot) }?.takeIf { !it.isTop } }
    val state = remember(entry, slot) { LazyListState(saved?.index ?: 0, saved?.offset ?: 0) }
    val restore = remember(entry, slot) { ScrollRestore(saved) }
    LaunchedEffect(state) {
        val target = saved ?: return@LaunchedEffect
        // المحتوى بيكبر (عدد العناصر · لسه فيه تحت) ⇒ نرجّع المكان تاني
        reapply(state.interactionSource, restore) {
            snapshotFlow { state.layoutInfo.totalItemsCount to state.canScrollForward }.collect { (count, _) ->
                if (count > target.index) state.scrollToItem(target.index, target.offset)
            }
        }
    }
    DisposableEffect(state) {
        onDispose {
            if (entry != null) nav.scrolls.saveList(entry, slot, restore.toSave(ScrollMemory.ListPos(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)))
        }
    }
    return state
}

/** نفس [rememberEntryLazyListState] لعمود متمرّر عادي (`verticalScroll` — زي «الأشخاص»). */
@Composable
fun rememberEntryScrollState(slot: String = "main"): ScrollState {
    val nav = LocalNavigator.current
    val entry = LocalEntryKey.current
    val saved = remember(entry, slot) { entry?.let { nav.scrolls.plain(it, slot) }?.takeIf { it > 0 } }
    val state = remember(entry, slot) { ScrollState(saved ?: 0) }
    val restore = remember(entry, slot) { ScrollRestore(saved) }
    LaunchedEffect(state) {
        val target = saved ?: return@LaunchedEffect
        reapply(state.interactionSource, restore) {
            snapshotFlow { state.maxValue }.collect { max -> if (max > 0) state.scrollTo(minOf(target, max)) }
        }
    }
    DisposableEffect(state) {
        onDispose { if (entry != null) nav.scrolls.savePlain(entry, slot, restore.toSave(state.value)) }
    }
    return state
}

/** يشغّل [block] (اللي بيرجّع المكان مع كل ما المحتوى يكبر) لحد [RESTORE_WINDOW_MS] أو أول سحبة منك — اللي ييجي الأول. */
private suspend fun reapply(source: InteractionSource, restore: ScrollRestore<*>, block: suspend () -> Unit) = coroutineScope {
    val job = launch { withTimeoutOrNull(RESTORE_WINDOW_MS) { block() } }
    val touch = launch {
        source.interactions.first { it is DragInteraction.Start }
        restore.touched()
        job.cancel()
    }
    job.join()
    touch.cancel()
    restore.finish()
}
