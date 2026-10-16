package app.masroufy.ui.character

import app.masroufy.core.CharacterLayer
import app.masroufy.core.CharacterLook
import java.io.File

/** شكل واحد من مجموعة الدقة (`golden/character/fidelity-set.json` — نفس اللي صفحة النموذج بترسمه). */
internal data class FidelityLook(val id: String, val layer: CharacterLayer, val look: CharacterLook)

/** مجلد `golden/character` (الاختبار بيشتغل من مجلد الموديول `native-app/ui`). */
internal val goldenCharacterDir: File = File("../golden/character")

private val ENTRY = Regex("\"id\":\"([^\"]+)\",\"mode\":\"([a-z]+)\",\"look\":\\{([^}]*)\\}")
private val FIELD = Regex("\"([a-zA-Z]+)\":(-?\\d+)")

/** قراية المجموعة: الحقل الناقص = افتراضي النموذج (`Object.assign({}, DEFAULT, look)`). */
internal fun readFidelitySet(): List<FidelityLook> = ENTRY.findAll(File(goldenCharacterDir, "fidelity-set.json").readText()).map { m ->
    val f = FIELD.findAll(m.groupValues[3]).associate { it.groupValues[1] to it.groupValues[2].toInt() }
    val d = CharacterLook()
    val look = CharacterLook(
        gender = f["gender"] ?: d.gender, build = f["build"] ?: d.build, face = f["face"] ?: d.face, skin = f["skin"] ?: d.skin,
        hair = f["hair"] ?: d.hair, hairColor = f["hairColor"] ?: d.hairColor, eyes = f["eyes"] ?: d.eyes, brows = f["brows"] ?: d.brows,
        nose = f["nose"] ?: d.nose, mouth = f["mouth"] ?: d.mouth, beard = f["beard"] ?: d.beard, glasses = f["glasses"] ?: d.glasses,
        head = f["head"] ?: d.head, wrap = f["wrap"] ?: d.wrap, outfit = f["outfit"] ?: d.outfit, outfitColor = f["outfitColor"] ?: d.outfitColor,
        hat = f["hat"] ?: d.hat, bling = f["bling"] ?: d.bling,
    )
    val layer = when (m.groupValues[2]) { "head" -> CharacterLayer.HEAD; "full" -> CharacterLayer.FULL; else -> CharacterLayer.BUST }
    FidelityLook(m.groupValues[1], layer, look)
}.toList()
