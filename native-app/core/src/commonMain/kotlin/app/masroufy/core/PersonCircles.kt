package app.masroufy.core

/**
 * شاشة الأشخاص — الدواير والعلاقات (قرار المالك 2026-10-05: «دواير متداخلة فوق — إنت في النص، والحلقات عيلة · صحاب · شغل —
 * وتحتها قايمة بالأرصدة»، وخطوط منقطة بين الناس اللي بينهم صلة زي أم وبنتها).
 *
 * **مستند الشخص اللي التطبيق الحالي بيقراه (`people`: id · name · archived) ما بيتغيرش خالص** — الدايرة وصلته بيك في مجموعة لوحدها
 * على مستوى الحساب ([PERSON_PROFILES_GROUP]، معرّف المستند = معرّف الشخص)، والعلاقات بين الأشخاص في [PERSON_RELATIONS_GROUP].
 * الاتنين **على مستوى الحساب** لأن الشخص مشترك بين البلاد (§41 · §64).
 */
const val PERSON_PROFILES_GROUP = "personProfiles"
const val PERSON_RELATIONS_GROUP = "personRelations"

/** الدواير والعلاقات — بيتكتبوا في النسخة الشاملة مع بعض بس لو أي واحدة فيها حاجة. */
val PEOPLE_BACKUP_GROUPS = listOf(PERSON_PROFILES_GROUP, PERSON_RELATIONS_GROUP)

/** صلة الشخص بيك أو صلة شخصين ببعض: نص حر قصير (أخي · زوجتي · أمها). */
const val RELATION_LABEL_MAX = 30

/** الدايرة. من غير دايرة ⇒ بيظهر في [OTHER] ([circleOf]). */
enum class PersonCircle(val wire: String, val labelKey: TextKey) {
    FAMILY("family", TextKey.PERSON_CIRCLE_FAMILY),
    FRIEND("friend", TextKey.PERSON_CIRCLE_FRIEND),
    WORK("work", TextKey.PERSON_CIRCLE_WORK),
    OTHER("other", TextKey.PERSON_CIRCLE_OTHER),
    ;

    val label: String get() = uiText(labelKey)

    companion object {
        fun fromWire(wire: String): PersonCircle = entries.first { it.wire == wire }
    }
}

/** دايرة الشخص وصلته بيك. [circle] null = لسه ما اتحددتش. */
data class PersonProfile(val personId: Id, val circle: PersonCircle? = null, val relationLabel: String? = null, val updatedAt: String)

/** الدايرة للعرض — اللي مالوش دايرة بيظهر «غيرهم». */
fun circleOf(profile: PersonProfile?): PersonCircle = profile?.circle ?: PersonCircle.OTHER

class PersonCircleError(message: String) : IllegalArgumentException(message)

/** نص الصلة نضيف: من غير مسافات زيادة، والفاضي null، والأطول من [RELATION_LABEL_MAX] مرفوض. */
fun cleanRelationLabel(label: String?): String? {
    val clean = label?.let { JsText.collapseWhitespace(JsText.trim(it)) }?.takeIf { it.isNotEmpty() } ?: return null
    if (clean.length > RELATION_LABEL_MAX) throw PersonCircleError(uiText(TextKey.PERSON_RELATION_LABEL_LENGTH, RELATION_LABEL_MAX.toString()))
    return clean
}

/**
 * صلة بين شخصين (خط منقط في الشاشة). الترتيب ما يفرقش: [personAId] دايمًا الأصغر في الترتيب، والمعرّف من الاتنين ([personRelationId]).
 * أرشفة أي واحد منهم **بتخبّي** الصلة ومش بتمسحها ([visibleRelations]).
 */
data class PersonRelation(val id: Id, val personAId: Id, val personBId: Id, val label: String? = null, val createdAt: String)

/**
 * معرّف ثابت من غير ترتيب: (أ، ب) = (ب، أ). طول المعرّف الأول جوه المعرّف عشان ما يحصلش تشابه
 * («a-b» + «c» غير «a» + «b-c»).
 */
fun personRelationId(a: Id, b: Id): Id {
    if (a == b) throw PersonCircleError(uiText(TextKey.PERSON_RELATION_SELF))
    val (x, y) = orderedPair(a, b)
    return "prel-${x.length}-$x-$y"
}

private fun orderedPair(a: Id, b: Id): Pair<Id, Id> = if (a <= b) a to b else b to a

/** مفتاح الزوج من غير ترتيب — للمقارنة (صلة اتدمجت من نسخة على شخص اتغير معرّفه). */
fun relationPairKey(r: PersonRelation): Pair<Id, Id> = orderedPair(r.personAId, r.personBId)

/**
 * صلة جديدة (أو تعديل اسم صلة موجودة لنفس الزوج — [existing]). الاتنين لازم يبقوا موجودين ومش مؤرشفين، ومفيش صلة لنفس الشخص.
 */
fun newPersonRelation(a: Id, b: Id, label: String?, people: List<Person>, now: String, existing: PersonRelation? = null): PersonRelation {
    val id = personRelationId(a, b)
    val byId = people.associateBy { it.id }
    val pa = byId[a] ?: throw PersonCircleError(uiText(TextKey.PEOPLE_PERSON_NOT_FOUND))
    val pb = byId[b] ?: throw PersonCircleError(uiText(TextKey.PEOPLE_PERSON_NOT_FOUND))
    if (pa.archived || pb.archived) throw PersonCircleError(uiText(TextKey.PERSON_RELATION_ARCHIVED))
    val (x, y) = orderedPair(a, b)
    return PersonRelation(existing?.id ?: id, x, y, cleanRelationLabel(label), existing?.createdAt ?: now)
}

/**
 * الصلات اللي بتظهر: الشخصين موجودين ومش مؤرشفين، ومن غير صلة لنفس الشخص، وزوج واحد مرة واحدة (الأقدم يكسب لو اتكرر من دمج نسخة).
 */
fun visibleRelations(relations: List<PersonRelation>, people: List<Person>): List<PersonRelation> {
    val active = people.filter { !it.archived }.map { it.id }.toSet()
    val seen = HashSet<Pair<Id, Id>>()
    return relations
        .filter { it.personAId != it.personBId && it.personAId in active && it.personBId in active }
        .sortedWith(compareBy<PersonRelation> { it.createdAt }.thenBy { it.id })
        .filter { seen.add(relationPairKey(it)) }
}
