package app.masroufy.ui.character

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.masroufy.core.CharacterLayer
import app.masroufy.core.CharacterLook
import app.masroufy.core.fitGender
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.screens.character.BuilderState
import app.masroufy.ui.screens.character.PreviewRow
import app.masroufy.ui.screens.people.StageCharacter
import app.masroufy.ui.shell.MeAvatar
import app.masroufy.ui.theme.LocalReduceMotion
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * «شخصيتك» في أماكنها (r041b) — صورة PNG واحدة (بس لو `CHARACTER_FRAME_DIR` متظبط، واسمها من `CHARACTER_FRAME_TAG`): لكل شكل
 * كارت «الأشخاص» 350×360 (ونقطة بداية الخيوط 175,180 ومنطقة الضغطة الطويلة 110–240 × 120–360 مرسومين خفيف) · صف المعاينة في البنّاء
 * («الجسم كله» و«قريب» ومعاهم مربّع «في الرئيسية» و«في الأشخاص») · `MeAvatar` 96 و46 وراس الرئيسية 64. للعين — مفيش مقارنة هنا.
 */
class CharacterFramePngTest {
    private val looks = listOf(
        CharacterLook.START,
        CharacterLook.START.fitGender(1),
        CharacterLook.START.copy(head = 1, build = 2),
        CharacterLook.START.fitGender(1).copy(head = 5, outfit = 5, build = 0),
    )

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun rendersPeopleCardBuilderPreviewsAndAvatars() {
        val out = System.getenv("CHARACTER_FRAME_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        out.mkdirs()
        val tag = System.getenv("CHARACTER_FRAME_TAG")?.takeIf { it.isNotBlank() } ?: "frames"
        val w = 16 + 350 + 16 + 360 + 16 + 110 + 16; val rowH = 460
        val scene = ImageComposeScene(w * 2, (rowH * looks.size + 16) * 2, Density(2f)) {
            CompositionLocalProvider(LocalReduceMotion provides true, LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(Modifier.fillMaxSize().background(Color(0xFFEFF3F0)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (look in looks) Row(Modifier.height((rowH - 16).dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PeopleCard(look)
                        Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PreviewRow(BuilderState(look, built = true, view = CharacterLayer.FULL)) {}
                            PreviewRow(BuilderState(look, built = true, view = CharacterLayer.BUST)) {}
                        }
                        Column(Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            MeAvatar(96.dp, null, character = look)
                            MeAvatar(46.dp, 60, character = look)
                            CharacterArt(look, CharacterLayer.HEAD, Modifier.size(64.dp))
                        }
                    }
                }
            }
        }
        try {
            File(out, "$tag.png").writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Composable
    private fun PeopleCard(look: CharacterLook) {
        HeroCard(Modifier.size(350.dp, 360.dp), contentPadding = PaddingValues(0.dp)) {
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                StageCharacter(look)
                Canvas(Modifier.fillMaxSize()) {
                    val k = density
                    drawCircle(Color(0xFFFFD54F), 4 * k, Offset(175 * k, 180 * k))
                    drawRect(Color(0x66FFFFFF), Offset(110 * k, 120 * k), Size(130 * k, 240 * k), style = Stroke(1 * k))
                }
            }
        }
    }
}
