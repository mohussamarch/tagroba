package app.masroufy.ui.screens.character

import app.masroufy.core.ArabicVariant
import app.masroufy.core.CharacterColorKey
import app.masroufy.core.CharacterLayer
import app.masroufy.core.CharacterLook
import app.masroufy.core.CharacterPart
import app.masroufy.core.CharacterSection
import app.masroufy.core.Language
import app.masroufy.core.Texts
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** البنّاء «ابنِ شخصيتك» (r041 — character-spec §3) كحالة نقية: الترتيب والنوع من الملف والمعاينة والألوان والملخصات. */
class CharacterBuilderStateTest {
    @AfterTest fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test fun opensOnFaceFeaturesInTheOwnersOrder() {
        val s = startBuilder(null, "male")
        assertEquals(CharacterSection.FACE, s.section)
        assertEquals(listOf(CharacterSection.FACE, CharacterSection.BODY, CharacterSection.WEAR), CharacterSection.entries)
        assertEquals(CharacterPart.FACE, openPart(s))
        assertTrue(sectionsBefore(s).isEmpty())
        assertEquals(listOf(CharacterSection.BODY, CharacterSection.WEAR), sectionsAfter(s))
        assertFalse(s.built)
        assertEquals("ابنِ شخصيتك", builderTitle(false))
        assertEquals("عدّل شخصيتك", builderTitle(true))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("ابني شخصيتك", builderTitle(false))
    }

    @Test fun startsWithoutABeardAndFollowsTheProfileGender() {
        val man = startBuilder(null, null)
        assertEquals(0, man.look.beard, "بداية النموذج من غير لحية")
        assertEquals(0, man.look.gender, "من غير نوع ⇒ قوايم الرجالة")
        val woman = startBuilder(CharacterLook(hair = 2, beard = 3, outfit = 1), "female")
        assertTrue(woman.built)
        assertEquals(1, woman.look.gender)
        assertEquals(0, woman.look.beard)
        assertEquals(5, woman.look.outfit)
        assertEquals(listOf(CharacterPart.BUILD, CharacterPart.SKIN, CharacterPart.HAIR), partsOf(woman, CharacterSection.BODY), "مفيش لحية للستات")
        assertEquals(listOf(6, 7, 8, 9, 10), optionsFor(woman, CharacterPart.HAIR).map { it.id })
        assertEquals(listOf(0, 4, 5, 6), optionsFor(woman, CharacterPart.HEAD).map { it.id })
        assertEquals(listOf(0, 1, 2, 3, 7), optionsFor(man, CharacterPart.HEAD).map { it.id })
        val savedWoman = startBuilder(CharacterLook(gender = 1, hair = 7, outfit = 3), null)
        assertEquals(1, savedWoman.look.gender, "ملف من غير نوع ⇒ الشخصية المحفوظة زي ما هي (من غير ظبط)")
        assertEquals(7, savedWoman.look.hair)
    }

    @Test fun thePreviewIsFullBodyOnClothesAndBuildUntilPickedByHand() {
        var s = openSection(startBuilder(null, "male"), CharacterSection.WEAR)
        assertEquals(CharacterLayer.FULL, previewLayer(s))
        s = openTab(s, 1)
        assertEquals(CharacterPart.HEAD, openPart(s))
        assertEquals(CharacterLayer.BUST, previewLayer(s))
        s = pickView(s, CharacterLayer.FULL)
        assertEquals(CharacterLayer.FULL, previewLayer(s), "اختاره بإيده ⇒ بيفضل")
        val body = openSection(startBuilder(null, "male"), CharacterSection.BODY)
        assertEquals(CharacterLayer.FULL, previewLayer(body), "هيئة الجسم")
        // التبويب بيتفتكر لكل قسم
        val back = openSection(openSection(s, CharacterSection.FACE), CharacterSection.WEAR)
        assertEquals(CharacterPart.HEAD, openPart(back))
    }

    @Test fun niqabTurnsBlackAndOutfitResetsItsColour() {
        var s = startBuilder(null, "female")
        s = pickColor(s, CharacterColorKey.OUTFIT, 3)
        s = pick(s, CharacterPart.HEAD, 6)
        assertEquals(2, s.look.wrap, "النقاب بيبدأ أسود")
        s = pick(s, CharacterPart.OUTFIT, 3)
        assertEquals(0, s.look.outfitColor, "تغيير اللبس ⇒ أول لون")
        assertEquals("لون النقاب", colorLabel(s, CharacterPart.HEAD))
        assertTrue(showColors(s, CharacterPart.HEAD))
    }

    @Test fun headCoverColoursOnlyForWrapsAndHiddenHairHasANote() {
        var s = openTab(openSection(startBuilder(null, "male"), CharacterSection.BODY), 2)
        assertEquals(CharacterPart.HAIR, openPart(s))
        assertTrue(showColors(s))
        assertFalse(hairNote(s))
        s = pick(s, CharacterPart.HEAD, 1)
        assertTrue(hairNote(s), "الشماغ بيخبّي الشعر")
        assertFalse(showColors(s), "ومن غير لون شعر")
        assertFalse(showColors(s, CharacterPart.HEAD), "الشماغ مالوش صف لون")
        s = pick(s, CharacterPart.HEAD, 7)
        assertTrue(showColors(s, CharacterPart.HEAD))
        assertEquals("لون الكاب", colorLabel(s, CharacterPart.HEAD))
        assertFalse(hairNote(s), "الكاب ما بيخبيش الشعر")
        // صور الشعر الصغيرة من غير غطا عشان الشعر يبان
        assertTrue(optionsFor(s, CharacterPart.HAIR).all { it.look.head == 0 && it.look.hat == 0 })
        assertEquals(CharacterLayer.BUST, optionsFor(s, CharacterPart.HAIR).first().layer)
        assertTrue(optionsFor(s, CharacterPart.SKIN).isEmpty(), "البشرة ألوان بس")
    }

    @Test fun summariesJoinTheCurrentPicks() {
        val s = startBuilder(CharacterLook(face = 1, eyes = 3, mouth = 2, build = 2, hair = 4, beard = 5, outfit = 2, head = 3, glasses = 1), "male")
        assertEquals("مستدير · واسعة · ابتسامة", summary(s, CharacterSection.FACE))
        assertEquals("ممتلئ · مجعّد · شارب فقط", summary(s, CharacterSection.BODY))
        assertEquals("بدلة · طاقية · مستديرة", summary(s, CharacterSection.WEAR))
        val plain = startBuilder(null, "female")
        assertEquals("عباية · بلا غطاء", summary(plain, CharacterSection.WEAR), "النظارة بتظهر لو فيه بس")
        assertEquals("متوسط · طويل ناعم", summary(plain, CharacterSection.BODY), "مفيش لحية للستات")
    }

    @Test fun optionThumbnailsChangeOnlyThatPart() {
        val s = startBuilder(CharacterLook(outfitColor = 4), "male")
        val outfits = optionsFor(s, CharacterPart.OUTFIT)
        assertEquals(listOf(0, 1, 2), outfits.map { it.id })
        assertTrue(outfits.all { it.look.outfitColor == 4 && it.layer == CharacterLayer.FULL })
        assertEquals(listOf(true, false, false), outfits.map { it.selected })
        assertEquals(CharacterLayer.HEAD, optionsFor(s, CharacterPart.EYES).first().layer)
    }
}
