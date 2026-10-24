package app.masroufy.firestore

import app.masroufy.core.ALERT_DISMISSALS_GROUP
import app.masroufy.core.ALERT_RECEIPTS_GROUP
import app.masroufy.core.BACKUP_GROUPS
import app.masroufy.core.COUNTRY_PACKS
import app.masroufy.core.LATER_BACKUP_GROUPS
import app.masroufy.core.SPACES_GROUP
import app.masroufy.core.SPACE_TRANSFERS_GROUP
import app.masroufy.core.USER_SETTINGS_GROUP
import app.masroufy.core.spaceIdForCountry
import app.masroufy.data.DocumentCodecs

/** علامة «المراجع اتجهزت»: `{root}/initialization/references-v1` ([FirestoreReferenceSeed]). */
internal const val INITIALIZATION_GROUP = "initialization"
internal const val REFERENCES_MARKER_ID = "references-v1"

/** رقم مراجعة السدادات: `{root}/concurrency/settlements` ([FirestoreSettlementWriter] · [FirestoreFullBackup]). */
internal const val CONCURRENCY_GROUP = "concurrency"
internal const val SETTLEMENTS_REVISION_ID = "settlements"

/**
 * r06-fix055b (BLOCKER «امسح حسابي»): **كل** مجموعة بيكتبها التطبيق تحت `users/{uid}` (ونفس الأسماء تحت كل `spaces/{id}`) — من نفس
 * الثوابت اللي المستودعات بتستعملها، عشان المسح والمستودعات ما يفترقوش:
 * كل المحوّلات ([DocumentCodecs.byGroup]) + مجموعات النسخة الشاملة + المستندات المفردة (الملف · علامة المراجع · مراجعة السدادات) +
 * سجل المساحات والتحويل بينها + أسماء قديمة كتبها تطبيق الويب/النسخ الأولى ([LEGACY_USER_GROUPS]).
 * `UserCollectionsGuardTest` بيمسح كود موديول firestore وبيفشل لو فيه مسار مجموعة تحت المستخدم مش هنا.
 */
val USER_COLLECTIONS: List<String> by lazy {
    (
        DocumentCodecs.byGroup.keys + BACKUP_GROUPS + LATER_BACKUP_GROUPS + ACCOUNT_DOC_GROUPS +
            listOf(INITIALIZATION_GROUP, CONCURRENCY_GROUP, SPACE_TRANSFERS_GROUP, ALERT_RECEIPTS_GROUP, ALERT_DISMISSALS_GROUP, USER_SETTINGS_GROUP) +
            LEGACY_USER_GROUPS
        ).filter { it != SPACES_GROUP }.distinct()
}

/** أسماء ممكن تكون اتكتبت قبل كده تحت المستخدم ومالهاش مستودع دلوقتي — بتتمسح احتياطي (المسح على مجموعة فاضية = قراية واحدة). */
val LEGACY_USER_GROUPS: List<String> = listOf("syncCursors", "feedback")

/** معرّفات البلاد المعروفة (`spaces/{id}`) — بتتمسح حتى لو مستند البلد نفسه اتمسح وفضل اللي تحته. */
val KNOWN_SPACE_IDS: List<String> get() = COUNTRY_PACKS.keys.map(::spaceIdForCountry)

/**
 * مجموعات **برا** `users/{uid}` — مش ملك المستخدم ومش بتتمسح مع الحساب (§80): `sharedMerchants` (أسامي تجار بس، من غير uid) و
 * `inbox` (رسايل الدعم — القواعد بتمنع القراية والمسح من الجوال، ومن غير uid).
 */
val SHARED_COLLECTIONS: List<String> = listOf(FirestoreSharedMerchantCatalog.COLLECTION, FirestoreFeedbackInbox.COLLECTION)
