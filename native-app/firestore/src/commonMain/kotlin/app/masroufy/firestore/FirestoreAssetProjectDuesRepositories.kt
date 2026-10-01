package app.masroufy.firestore

import app.masroufy.core.Asset
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetPrice
import app.masroufy.core.AssetSale
import app.masroufy.core.DebtTerms
import app.masroufy.core.Id
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.Project
import app.masroufy.core.ProjectLink
import app.masroufy.core.ProjectRule
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry
import app.masroufy.data.AssetProjectCodecs
import app.masroufy.data.DuesCodecs
import app.masroufy.port.AssetLotRepository
import app.masroufy.port.AssetPriceRepository
import app.masroufy.port.AssetRepository
import app.masroufy.port.AssetSaleRepository
import app.masroufy.port.DebtTermsRepository
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.ProjectLinkRepository
import app.masroufy.port.ProjectRepository
import app.masroufy.port.ProjectRuleRepository
import app.masroufy.port.RoscaEntryRepository
import app.masroufy.port.RoscaRepository

/** الأصول والمشاريع («المستحقات» تحت) — نقل `assetRepositories.ts` و`projectRepositories.ts`. */

class FirestoreAssetRepository(private val space: FirestoreSpace) : AssetRepository {
    private val codec = AssetProjectCodecs.assets

    override suspend fun listAll(): List<Asset> = codec.decodeAll(space.collection(codec.group).get(space.sourceFor(codec.group)))

    override suspend fun save(asset: Asset) = space.saveAll(codec, listOf(asset))
}

class FirestoreAssetLotRepository(private val space: FirestoreSpace) : AssetLotRepository {
    private val codec = AssetProjectCodecs.assetLots

    override suspend fun listByAsset(assetId: Id): List<AssetLot> = codec.decodeAll(space.collection(codec.group).where { "assetId" equalTo assetId }.get(space.sourceFor(codec.group)))

    override suspend fun listAll(): List<AssetLot> = codec.decodeAll(space.collection(codec.group).get(space.sourceFor(codec.group)))

    override suspend fun saveMany(lots: List<AssetLot>) = space.saveAll(codec, lots)

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}

class FirestoreAssetSaleRepository(private val space: FirestoreSpace) : AssetSaleRepository {
    private val codec = AssetProjectCodecs.assetSales

    override suspend fun listByAsset(assetId: Id): List<AssetSale> = codec.decodeAll(space.collection(codec.group).where { "assetId" equalTo assetId }.get(space.sourceFor(codec.group)))

    override suspend fun listAll(): List<AssetSale> = codec.decodeAll(space.collection(codec.group).get(space.sourceFor(codec.group)))

    override suspend fun saveMany(sales: List<AssetSale>) = space.saveAll(codec, sales)

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}

/** سعر واحد لكل أصل — معرّف المستند معرّف الأصل. */
class FirestoreAssetPriceRepository(private val space: FirestoreSpace) : AssetPriceRepository {
    private val codec = AssetProjectCodecs.assetPrices

    override suspend fun listAll(): List<AssetPrice> = codec.decodeAll(space.collection(codec.group).get(space.sourceFor(codec.group)))

    override suspend fun save(price: AssetPrice) = space.saveAll(codec, listOf(price))
}

class FirestoreProjectRepository(private val space: FirestoreSpace) : ProjectRepository {
    private val codec = AssetProjectCodecs.projects

    override suspend fun listAll(): List<Project> = codec.decodeAll(space.collection(codec.group).get(space.sourceFor(codec.group)))

    override suspend fun save(project: Project) = space.saveAll(codec, listOf(project))
}

class FirestoreProjectRuleRepository(private val space: FirestoreSpace) : ProjectRuleRepository {
    private val codec = AssetProjectCodecs.projectRules

    override suspend fun listAll(): List<ProjectRule> = codec.decodeAll(space.collection(codec.group).get(space.sourceFor(codec.group)))

    override suspend fun save(rule: ProjectRule) = space.saveAll(codec, listOf(rule))
}

class FirestoreProjectLinkRepository(private val space: FirestoreSpace) : ProjectLinkRepository {
    private val codec = AssetProjectCodecs.projectLinks

    override suspend fun listAll(): List<ProjectLink> = codec.decodeAll(space.collection(codec.group).get(space.sourceFor(codec.group)))

    override suspend fun listByTransaction(transactionId: Id): List<ProjectLink> =
        codec.decodeAll(space.collection(codec.group).where { "transactionId" equalTo transactionId }.get(space.sourceFor(codec.group)))

    override suspend fun saveMany(links: List<ProjectLink>) = space.saveAll(codec, links)
}

/** «المستحقات» (OVERRIDES §50) — مجموعات كوتلن بس، والقواعد بتسمح بيها من غير نشر (`DuesCodecs`). */
class FirestoreRoscaRepository(private val space: FirestoreSpace) : RoscaRepository {
    private val codec = DuesCodecs.roscas

    override suspend fun listAll(): List<Rosca> = codec.decodeAll(space.collection(codec.group).get(space.sourceFor(codec.group)))

    override suspend fun save(rosca: Rosca) = space.saveAll(codec, listOf(rosca))
}

class FirestoreRoscaEntryRepository(private val space: FirestoreSpace) : RoscaEntryRepository {
    private val codec = DuesCodecs.roscaEntries

    override suspend fun listByRosca(roscaId: Id): List<RoscaEntry> = codec.decodeAll(space.collection(codec.group).where { "roscaId" equalTo roscaId }.get(space.sourceFor(codec.group)))

    override suspend fun listByTransactionIds(ids: List<Id>): List<RoscaEntry> = space.findIn(codec, "transactionId", ids)

    override suspend fun saveMany(entries: List<RoscaEntry>) = space.saveAll(codec, entries)

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}

class FirestoreInstallmentPlanRepository(private val space: FirestoreSpace) : InstallmentPlanRepository {
    private val codec = DuesCodecs.installmentPlans

    override suspend fun listAll(): List<InstallmentPlan> = codec.decodeAll(space.collection(codec.group).get(space.sourceFor(codec.group)))

    override suspend fun save(plan: InstallmentPlan) = space.saveAll(codec, listOf(plan))
}

class FirestoreInstallmentPaymentRepository(private val space: FirestoreSpace) : InstallmentPaymentRepository {
    private val codec = DuesCodecs.installmentPayments

    override suspend fun listByPlan(planId: Id): List<InstallmentPayment> = codec.decodeAll(space.collection(codec.group).where { "planId" equalTo planId }.get(space.sourceFor(codec.group)))

    override suspend fun listByTransactionIds(ids: List<Id>): List<InstallmentPayment> = space.findIn(codec, "transactionId", ids)

    override suspend fun saveMany(payments: List<InstallmentPayment>) = space.saveAll(codec, payments)

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}

/** مستند لكل دين — المعرّف معرّف الدين. */
class FirestoreDebtTermsRepository(private val space: FirestoreSpace) : DebtTermsRepository {
    private val codec = DuesCodecs.debtTerms

    override suspend fun listAll(): List<DebtTerms> = codec.decodeAll(space.collection(codec.group).get(space.sourceFor(codec.group)))

    override suspend fun save(terms: DebtTerms) = space.saveAll(codec, listOf(terms))

    override suspend fun remove(obligationId: Id) = space.deleteAll(codec.group, listOf(obligationId))
}
