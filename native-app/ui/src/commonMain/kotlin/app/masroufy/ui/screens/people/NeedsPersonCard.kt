package app.masroufy.ui.screens.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.UiKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.screens.operations.MeaningTarget
import app.masroufy.ui.screens.operations.PersonLinked
import app.masroufy.ui.screens.operations.PersonMeaningSheet
import app.masroufy.ui.screens.operations.UndoStrip
import app.masroufy.ui.screens.operations.undoWindowFor
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.NeedsPersonRow
import kotlinx.coroutines.launch

/** كام سطر بيتعرض في الكارت قبل «و{العدد} كمان». */
internal const val NEEDS_PERSON_SHOWN = 5

/**
 * «محتاجة تربطها بشخص» (جولة r05 — طريق المالك نفسه: علّم العمليات بتصنيف معناه فلوس مع شخص ومحدش اتربط): العمليات دي كلها في كارت واحد على
 * «الديون» و«لك/عليك» — ضغطة على أي واحدة ⇒ «مع مين؟» ⇒ ربط بـ«تراجع». مفيش ⇒ الكارت ما بيظهرش.
 */
@Composable
internal fun NeedsPersonCard(modifier: Modifier = Modifier, personId: String? = null) {
    val space = LocalSpace.current
    val links = space.people.links ?: return
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val version = PeopleChanges.version
    var rows by remember { mutableStateOf<List<NeedsPersonRow>>(emptyList()) }
    LaunchedEffect(links, version) { rows = runCatching { links.needsPerson() }.getOrDefault(emptyList()) }
    var target by remember { mutableStateOf<MeaningTarget?>(null) }
    var undo by remember { mutableStateOf<PersonLinked?>(null) }
    val window = undoWindowFor(undo) { undo = null }
    val today = space.shell.today()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        undo?.let { u ->
            UndoStrip(u.text, window) {
                undo = null
                scope.launch {
                    toaster.show(runCatching { links.unlink(u.transactionId) }.exceptionOrNull()?.message ?: t(UiKey.PLINK_UNDONE))
                    PeopleChanges.bump()
                }
            }
        }
        if (rows.isNotEmpty()) FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(Lucide.USERS, size = 18.dp, tint = Ink.focus)
                BasicText(t(UiKey.PLINK_NEEDS_TITLE), Modifier.weight(1f), style = Type.bodyBold())
                Pill(sentenceNumber(rows.size), Ink.focus, Ink.alertBg)
            }
            BasicText(t(UiKey.PLINK_NEEDS_LIST_BODY), Modifier.padding(vertical = 4.dp), style = Type.caption().copy(color = Ink.muted))
            val shown = rows.take(NEEDS_PERSON_SHOWN)
            shown.forEachIndexed { i, r ->
                OpRow(r.transaction, r.categoryName, today, i == shown.lastIndex && rows.size <= NEEDS_PERSON_SHOWN) { target = targetOf(r.transaction, personId) }
            }
            if (rows.size > NEEDS_PERSON_SHOWN) {
                BasicText(t(UiKey.PLINK_NEEDS_MORE, sentenceNumber(rows.size - NEEDS_PERSON_SHOWN)), Modifier.padding(vertical = 10.dp), style = Type.caption().copy(color = Ink.muted))
            }
        }
    }
    PersonMeaningSheet(target, links, space.people.people, onDismiss = { target = null }) { linked ->
        target = null
        undo = linked
        PeopleChanges.bump()
    }
}
