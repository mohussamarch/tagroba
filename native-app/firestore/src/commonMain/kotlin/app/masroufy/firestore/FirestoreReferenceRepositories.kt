package app.masroufy.firestore

import app.masroufy.core.Category
import app.masroufy.core.ClassificationRule
import app.masroufy.core.Id
import app.masroufy.core.Merchant
import app.masroufy.core.Person
import app.masroufy.core.Wallet
import app.masroufy.core.normalizeText
import app.masroufy.data.ReferenceCodecs
import app.masroufy.port.CategoryRepository
import app.masroufy.port.MerchantRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.RuleRepository
import app.masroufy.port.WalletRepository

/** التصنيفات والقواعد والتجار والمحافظ والأشخاص على فايربيز — نقل `referenceRepositories.ts` و`walletRepository.ts` و`peopleRepositories.ts`. */

/** بترتيب `order` — زي التطبيق الحالي. */
class FirestoreCategoryRepository(private val space: FirestoreSpace) : CategoryRepository {
    private val codec = ReferenceCodecs.categories

    override suspend fun listAll(): List<Category> = space.select(codec).sortedBy { it.order }

    override suspend fun save(category: Category) = space.saveAll(codec, listOf(category))
}

/** بترتيب الأولوية (الأصغر الأول) — زي التطبيق الحالي. */
class FirestoreRuleRepository(private val space: FirestoreSpace) : RuleRepository {
    private val codec = ReferenceCodecs.rules

    override suspend fun listAll(): List<ClassificationRule> = space.select(codec).sortedBy { it.priority }

    override suspend fun saveMany(rules: List<ClassificationRule>) = space.saveAll(codec, rules)

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}

class FirestoreMerchantRepository(private val space: FirestoreSpace) : MerchantRepository {
    private val codec = ReferenceCodecs.merchants

    override suspend fun listAll(): List<Merchant> = space.select(codec)

    /** الاسم الأساسي الأول وبعده الأسماء البديلة — نفس الترتيب ونفس التطبيع بتاع التطبيق الحالي. */
    override suspend fun findByNormalizedName(normalizedName: String): Merchant? {
        val key = normalizeText(normalizedName)
        space.select(codec, DocQuery(listOf(Cond.Eq("normalizedName", key)), limit = 1)).firstOrNull()?.let { return it }
        return space.select(codec, DocQuery(listOf(Cond.Contains("aliases", key)), limit = 1)).firstOrNull()
    }

    override suspend fun saveMany(merchants: List<Merchant>) = space.saveAll(codec, merchants)
}

/**
 * المحافظ. ⚠️ `accountLast4` **آخر 4 أرقام بس** وقت الحفظ (CLAUDE.md #11، OVERRIDES §2) — ولو مفيش أرقام الحقل
 * ما بيتكتبش خالص. الحماية هنا عند حدود الطبقة، مش معتمدة على اللي بينادي (زي `last4Only` في التطبيق الحالي).
 */
class FirestoreWalletRepository(private val space: FirestoreSpace) : WalletRepository {
    private val codec = ReferenceCodecs.wallets

    override suspend fun listAll(): List<Wallet> = space.select(codec)

    override suspend fun findById(id: Id): Wallet? = space.readDoc(codec.group, id)?.let(codec::decode)

    override suspend fun save(wallet: Wallet) = space.saveAll(codec, listOf(wallet.copy(accountLast4 = last4Only(wallet.accountLast4))))
}

internal fun last4Only(value: String?): String? = value?.filter { it in '0'..'9' }?.takeLast(4)?.ifEmpty { null }

class FirestorePersonRepository(private val space: FirestoreSpace) : PersonRepository {
    private val codec = ReferenceCodecs.people

    override suspend fun listAll(): List<Person> = space.select(codec)

    override suspend fun save(person: Person) = space.saveAll(codec, listOf(person))
}
