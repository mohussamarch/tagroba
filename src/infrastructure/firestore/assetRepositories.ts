import {
  collection,
  doc,
  getDocs,
  query,
  setDoc,
  where,
  writeBatch,
  type Firestore,
} from 'firebase/firestore'
import type { Asset, AssetLot, AssetPrice, AssetSale } from '../../domain/entities/assets'
import type { Id } from '../../domain/entities/types'
import type {
  AssetLotRepository,
  AssetPriceRepository,
  AssetRepository,
  AssetSaleRepository,
} from '../../application/ports/repositories'

/**
 * مستودعات الاستثمار على Firestore.
 *
 * كل استعلام **بحقل واحد** (`assetId`) أو بالمجموعة كاملة —
 * لا فهرس مركّب (ARCHITECTURE §12). الترتيب الزمني في الذاكرة بعد القراءة.
 */

const BATCH_LIMIT = 500

function userPath(uid: string, ...segments: string[]): string {
  return ['users', uid, ...segments].join('/')
}

/** Firestore يرفض `undefined`، فالحقول الاختيارية تُحذف لا تُكتب فارغة. */
function clean<T extends object>(value: T): T {
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(value)) if (v !== undefined) out[k] = v
  return out as T
}

async function saveAll<T extends { id: Id }>(
  db: Firestore,
  uid: string,
  name: string,
  items: readonly T[],
): Promise<void> {
  for (let i = 0; i < items.length; i += BATCH_LIMIT) {
    const batch = writeBatch(db)
    for (const item of items.slice(i, i + BATCH_LIMIT)) {
      batch.set(doc(db, userPath(uid, name, item.id)), clean(item))
    }
    await batch.commit()
  }
}

async function deleteAll(
  db: Firestore,
  uid: string,
  name: string,
  ids: readonly Id[],
): Promise<void> {
  for (let i = 0; i < ids.length; i += BATCH_LIMIT) {
    const batch = writeBatch(db)
    for (const id of ids.slice(i, i + BATCH_LIMIT)) batch.delete(doc(db, userPath(uid, name, id)))
    await batch.commit()
  }
}

export class FirestoreAssetRepository implements AssetRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  async listAll(): Promise<Asset[]> {
    const snap = await getDocs(collection(this.db, userPath(this.uid, 'assets')))
    return snap.docs.map((d) => d.data() as Asset)
  }

  async save(asset: Asset): Promise<void> {
    await setDoc(doc(this.db, userPath(this.uid, 'assets', asset.id)), clean(asset))
  }
}

/** جسم مشترك بين الدفعات والمبيعات — نفس المسار ونفس الاستعلام. */
class FirestoreByAsset<T extends { id: Id; assetId: Id }> {
  constructor(
    protected readonly db: Firestore,
    protected readonly uid: string,
    protected readonly name: string,
  ) {}

  async listByAsset(assetId: Id): Promise<T[]> {
    const snap = await getDocs(
      query(collection(this.db, userPath(this.uid, this.name)), where('assetId', '==', assetId)),
    )
    return snap.docs.map((d) => d.data() as T)
  }

  async listAll(): Promise<T[]> {
    const snap = await getDocs(collection(this.db, userPath(this.uid, this.name)))
    return snap.docs.map((d) => d.data() as T)
  }

  async saveMany(items: readonly T[]): Promise<void> {
    await saveAll(this.db, this.uid, this.name, items)
  }

  async deleteMany(ids: readonly Id[]): Promise<void> {
    await deleteAll(this.db, this.uid, this.name, ids)
  }
}

export class FirestoreAssetLotRepository
  extends FirestoreByAsset<AssetLot>
  implements AssetLotRepository
{
  constructor(db: Firestore, uid: string) {
    super(db, uid, 'assetLots')
  }
}

export class FirestoreAssetSaleRepository
  extends FirestoreByAsset<AssetSale>
  implements AssetSaleRepository
{
  constructor(db: Firestore, uid: string) {
    super(db, uid, 'assetSales')
  }
}

/** سعر واحد لكل أصل، معرّف الوثيقة هو `assetId`. */
export class FirestoreAssetPriceRepository implements AssetPriceRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  async listAll(): Promise<AssetPrice[]> {
    const snap = await getDocs(collection(this.db, userPath(this.uid, 'assetPrices')))
    return snap.docs.map((d) => d.data() as AssetPrice)
  }

  async save(price: AssetPrice): Promise<void> {
    await setDoc(doc(this.db, userPath(this.uid, 'assetPrices', price.assetId)), clean(price))
  }
}
