package app.masroufy.data

import app.masroufy.core.PERSON_PROFILES_GROUP
import app.masroufy.core.PERSON_RELATIONS_GROUP
import app.masroufy.core.PersonCircle
import app.masroufy.core.PersonProfile
import app.masroufy.core.PersonRelation

/**
 * دواير الأشخاص والصلات بينهم (جلسة 16) — مجموعتين جداد على مستوى الحساب، في كوتلن بس. **مستند الشخص (`people`) ما اتلمسش**:
 * التطبيق الحالي بيقرا نفس الشكل (id · name · archived). الاختياري ما بيتكتبش، وقواعد فايربيز (`users/{uid}/{document=**}`)
 * بتغطيهم ⇒ مفيش نشر.
 */
object PersonCircleCodecs {
    /** معرّف المستند = معرّف الشخص ⇒ دايرة واحدة للشخص. */
    val personProfiles: DocCodec<PersonProfile> = codec(
        PERSON_PROFILES_GROUP, { it.personId },
        { p -> doc { req("personId", p.personId); opt("circle", p.circle?.wire); opt("relationLabel", p.relationLabel); req("updatedAt", p.updatedAt) } },
        { d ->
            PersonProfile(
                d.str("personId"), if (d.has("circle")) d.wire("circle", PersonCircle::fromWire) else null, d.strOrNull("relationLabel"), d.str("updatedAt"),
            )
        },
    )

    val personRelations: DocCodec<PersonRelation> = codec(
        PERSON_RELATIONS_GROUP, { it.id },
        { r -> doc { req("id", r.id); req("personAId", r.personAId); req("personBId", r.personBId); opt("label", r.label); req("createdAt", r.createdAt) } },
        { d -> PersonRelation(d.str("id"), d.str("personAId"), d.str("personBId"), d.strOrNull("label"), d.str("createdAt")) },
    )
}
