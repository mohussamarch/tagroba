package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.masroufy.ui.theme.Space

/**
 * هيكل شاشات المستحقات الداخلية: نفس `InnerScaffold` (الحواف 20 · المسافة 14 · من غير الشريطين) بس الرأس فيه سطر تحت العنوان
 * ([DuesHeader]) زي لوحات `DuesDebts` و`InstallmentDetail` و`SubscriptionDetail`.
 * [bottomBar] = زرارين ثابتين تحت (معالج الجمعية): فوق تدرّج من لون الخلفية (`rgba(239,243,237,0→.94)`) والقايمة بتسيب مكانهم (116).
 */
@Composable
internal fun DuesScaffold(
    title: String,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    bottomBar: (@Composable RowScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.gutter, end = Space.gutter, top = Space.gutter + top,
                bottom = (if (bottomBar != null) 116.dp else 48.dp) + bottom,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.block),
        ) {
            item(key = "header") { DuesHeader(title, subtitle, actions) }
            content()
        }
        if (bottomBar != null) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color(0x00EFF3ED), 0.3f to Color(0xF0EFF3ED), 1f to Color(0xF0EFF3ED)))
                    .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = bottomBar,
            )
        }
    }
}
