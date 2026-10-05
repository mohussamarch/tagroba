package app.masroufy.core

/**
 * نصوص شاشة الأشخاص (الدواير والعلاقات والملخص) باللغتين — فصحى (`MSA_PEOPLE_TEXTS`) ومصري (`EGYPTIAN_PEOPLE_TEXTS`) وإنجليزي — §66.
 * العربي **فصحى مختصرة** (قرار المالك §66). النسخة المصري (لمصر) لسه — بتيجي من آلية النسختين لما تتدمج.
 * الإنجليزي كتابة Claude ومستني مراجعة المالك زي باقي الجدول (§40).
 */
internal val MSA_PEOPLE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.PERSON_CIRCLE_FAMILY to "العائلة",
    TextKey.PERSON_CIRCLE_FRIEND to "الأصدقاء",
    TextKey.PERSON_CIRCLE_WORK to "العمل",
    TextKey.PERSON_CIRCLE_OTHER to "آخرون",
    TextKey.PERSON_RELATION_LABEL_LENGTH to "وصف الصلة {0} حرفًا على الأكثر",
    TextKey.PERSON_RELATION_SELF to "لا يمكن ربط الشخص بنفسه",
    TextKey.PERSON_RELATION_ARCHIVED to "لا يمكن ربط شخص مؤرشف",
    TextKey.PERSON_RELATION_NOT_FOUND to "الصلة غير موجودة",
    TextKey.PEOPLE_PERSON_NOT_FOUND to "الشخص غير موجود",
    TextKey.PEOPLE_SECTION_OWED_TO_YOU to "لك عندهم",
    TextKey.PEOPLE_SECTION_YOU_OWE to "عليك لهم",
    TextKey.PEOPLE_SECTION_OCCASIONS_SOON to "مناسبات قريبة",
    TextKey.PEOPLE_SECTION_NO_BALANCE to "بلا أرصدة",
    TextKey.PEOPLE_LINE_OWED_TO_YOU to "لك عنده {0}",
    TextKey.PEOPLE_LINE_YOU_OWE to "عليك له {0}",
    TextKey.PEOPLE_ME to "أنا",
    TextKey.BACKUP_GROUP_PERSON_PROFILES to "دوائر الأشخاص",
    TextKey.BACKUP_GROUP_PERSON_RELATIONS to "الصلات بين الأشخاص",
)

/** نفس المفاتيح بالمصري — لما البلد النشطة مصر (§66). */
internal val EGYPTIAN_PEOPLE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.PERSON_CIRCLE_FAMILY to "العيلة",
    TextKey.PERSON_CIRCLE_FRIEND to "الصحاب",
    TextKey.PERSON_CIRCLE_WORK to "الشغل",
    TextKey.PERSON_CIRCLE_OTHER to "تانيين",
    TextKey.PERSON_RELATION_LABEL_LENGTH to "وصف العلاقة {0} حرف بالكتير",
    TextKey.PERSON_RELATION_SELF to "مينفعش تربط الشخص بنفسه",
    TextKey.PERSON_RELATION_ARCHIVED to "مينفعش تربط شخص متأرشف",
    TextKey.PERSON_RELATION_NOT_FOUND to "العلاقة مش موجودة",
    TextKey.PEOPLE_PERSON_NOT_FOUND to "الشخص مش موجود",
    TextKey.PEOPLE_SECTION_OWED_TO_YOU to "ليك عندهم",
    TextKey.PEOPLE_SECTION_YOU_OWE to "عليك ليهم",
    TextKey.PEOPLE_SECTION_OCCASIONS_SOON to "مناسبات قربت",
    TextKey.PEOPLE_SECTION_NO_BALANCE to "مفيش حساب بينكم",
    TextKey.PEOPLE_LINE_OWED_TO_YOU to "ليك عنده {0}",
    TextKey.PEOPLE_LINE_YOU_OWE to "عليك له {0}",
    TextKey.PEOPLE_ME to "أنا",
    TextKey.BACKUP_GROUP_PERSON_PROFILES to "دواير الأشخاص",
    TextKey.BACKUP_GROUP_PERSON_RELATIONS to "العلاقات بين الأشخاص",
)

internal val ENGLISH_PEOPLE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.PERSON_CIRCLE_FAMILY to "Family",
    TextKey.PERSON_CIRCLE_FRIEND to "Friends",
    TextKey.PERSON_CIRCLE_WORK to "Work",
    TextKey.PERSON_CIRCLE_OTHER to "Others",
    TextKey.PERSON_RELATION_LABEL_LENGTH to "The relation label can be at most {0} characters",
    TextKey.PERSON_RELATION_SELF to "A person can't be related to themselves",
    TextKey.PERSON_RELATION_ARCHIVED to "An archived person can't be related",
    TextKey.PERSON_RELATION_NOT_FOUND to "Relation not found",
    TextKey.PEOPLE_PERSON_NOT_FOUND to "Person not found",
    TextKey.PEOPLE_SECTION_OWED_TO_YOU to "They owe you",
    TextKey.PEOPLE_SECTION_YOU_OWE to "You owe them",
    TextKey.PEOPLE_SECTION_OCCASIONS_SOON to "Upcoming occasions",
    TextKey.PEOPLE_SECTION_NO_BALANCE to "No balances",
    TextKey.PEOPLE_LINE_OWED_TO_YOU to "Owes you {0}",
    TextKey.PEOPLE_LINE_YOU_OWE to "You owe {0}",
    TextKey.PEOPLE_ME to "Me",
    TextKey.BACKUP_GROUP_PERSON_PROFILES to "People circles",
    TextKey.BACKUP_GROUP_PERSON_RELATIONS to "Relations between people",
)
