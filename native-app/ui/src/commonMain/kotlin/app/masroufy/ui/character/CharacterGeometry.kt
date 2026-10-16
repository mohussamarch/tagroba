// ART-STUB (replaced by r041-char-art at merge)
package app.masroufy.ui.character

import androidx.compose.ui.graphics.Color
import app.masroufy.core.CharacterColorKey
import app.masroufy.core.CharacterLook

/** ألوان النموذج (character-spec §2.2) — البشرة هنا لون الوش بس (اللي بيظهر في دواير الألوان). */
object CharacterPalette {
    val skin: List<Color> = listOf(0xFFF3D5C0, 0xFFE8BE9C, 0xFFD4A07A, 0xFFB57E58, 0xFF8C5A3C, 0xFF5E3C28).map { Color(it) }
    val hair: List<Color> = listOf(0xFF1E1916, 0xFF3A2A1F, 0xFF6A4A2F, 0xFFA47A4E, 0xFFC8A46C, 0xFF9B9A95).map { Color(it) }
    val wrap: List<Color> = listOf(0xFF26364A, 0xFF5E4634, 0xFF1E1E1E, 0xFF7E8C88, 0xFF8E4A57, 0xFFE9E2D6).map { Color(it) }
    val outfit: List<Color> = listOf(0xFFF4F3EE, 0xFF2E5E8C, 0xFF3D4A45, 0xFF8A6A4A, 0xFF0E6B57, 0xFF7A4655).map { Color(it) }
    val abaya: List<Color> = listOf(0xFF1C1C1E, 0xFF2B3140, 0xFF4A3B32, 0xFF585D5A, 0xFF5E2F3B, 0xFFD8CBB6).map { Color(it) }
}

fun CharacterPalette.forKey(key: CharacterColorKey, look: CharacterLook): List<Color> = when (key) {
    CharacterColorKey.SKIN -> skin
    CharacterColorKey.HAIR -> hair
    CharacterColorKey.WRAP -> wrap
    CharacterColorKey.OUTFIT -> if (look.outfit == 5) abaya else outfit
}
