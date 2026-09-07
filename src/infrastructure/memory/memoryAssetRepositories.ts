import type { Asset, AssetLot, AssetPrice, AssetSale } from '../../domain/entities/assets'
import type { Id } from '../../domain/entities/types'
import type {
  AssetLotRepository,
  AssetPriceRepository,
  AssetRepository,
  AssetSaleRepository,
} from '../../application/ports/repositories'
import type { Snapshotable } from './memoryRepositories'

/** مستودعات الاستثمار في الذاكرة — للاختبار بلا فايربيز (CLAUDE.md #6). */

const clone = <T>(value: T): T => structuredClone(value)

export class MemoryAssetRepository implements AssetRepository, Snapshotable<Asset[]> {
  private items = new Map<Id, Asset>()

  constructor(seed: readonly Asset[] = []) {
    for (const a of seed) this.items.set(a.id, clone(a))
  }

  async listAll(): Promise<Asset[]> {
    return [...this.items.values()].map(clone)
  }

  async save(asset: Asset): Promise<void> {
    this.items.set(asset.id, clone(asset))
  }

  snapshot(): Asset[] {
    return [...this.items.values()].map(clone)
  }

  restore(state: Asset[]): void {
    this.items = new Map(state.map((a) => [a.id, clone(a)]))
  }
}

/** جسم مشترك بين الدفعات والمبيعات — نفس الشكل، نفس الاستعلامات. */
class MemoryByAsset<T extends { id: Id; assetId: Id }> implements Snapshotable<T[]> {
  protected items = new Map<Id, T>()

  constructor(seed: readonly T[] = []) {
    for (const item of seed) this.items.set(item.id, clone(item))
  }

  async listByAsset(assetId: Id): Promise<T[]> {
    return [...this.items.values()].filter((i) => i.assetId === assetId).map(clone)
  }

  async listAll(): Promise<T[]> {
    return [...this.items.values()].map(clone)
  }

  async saveMany(items: readonly T[]): Promise<void> {
    for (const item of items) this.items.set(item.id, clone(item))
  }

  async deleteMany(ids: readonly Id[]): Promise<void> {
    for (const id of ids) this.items.delete(id)
  }

  snapshot(): T[] {
    return [...this.items.values()].map(clone)
  }

  restore(state: T[]): void {
    this.items = new Map(state.map((i) => [i.id, clone(i)]))
  }
}

export class MemoryAssetLotRepository
  extends MemoryByAsset<AssetLot>
  implements AssetLotRepository {}

export class MemoryAssetSaleRepository
  extends MemoryByAsset<AssetSale>
  implements AssetSaleRepository {}

/** سعر واحد لكل أصل — مفتاحه `assetId` لا معرّف مستقل. */
export class MemoryAssetPriceRepository
  implements AssetPriceRepository, Snapshotable<AssetPrice[]>
{
  private items = new Map<Id, AssetPrice>()

  constructor(seed: readonly AssetPrice[] = []) {
    for (const p of seed) this.items.set(p.assetId, clone(p))
  }

  async listAll(): Promise<AssetPrice[]> {
    return [...this.items.values()].map(clone)
  }

  async save(price: AssetPrice): Promise<void> {
    this.items.set(price.assetId, clone(price))
  }

  snapshot(): AssetPrice[] {
    return [...this.items.values()].map(clone)
  }

  restore(state: AssetPrice[]): void {
    this.items = new Map(state.map((p) => [p.assetId, clone(p)]))
  }
}
