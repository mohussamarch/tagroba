import { parseMoney } from '../../domain/money'
import type { Wallet } from '../../domain/entities/types'
import type { WalletRepository } from '../ports/repositories'

/**
 * SeedWallets — المحفظتان المعتمدتان في `OVERRIDES §6`.
 *
 * | المحفظة | الرصيد الافتتاحي | التاريخ |
 * |---|---|---|
 * | الراجحي (بنك) | 4,837.83 ر.س | 2025-01-01 |
 * | الكاش | 1,000.00 ر.س | 2026-09-06 |
 *
 * الرصيد البنكي مشتق من الكشف نفسه: أول عملية مدين 96.47 والرصيد
 * بعدها 4,741.36، فالافتتاحي 4,837.83 — وهو نقطة بداية سلسلة المطابقة.
 *
 * ⚠️ **الزرع مرة واحدة فقط.** لو وُجدت محافظ فالمستخدم عدّلها أو أضاف
 * غيرها، ولا يجوز إعادة الكتابة فوقها.
 *
 * ⚠️ هذه أرقام **المالك**. أي مستخدم آخر للتطبيق يبدأ بأرصدة صفرية —
 * لا يجوز أن يرث أحد رصيد أحد.
 */

/** معرّفات ثابتة: مطلوبة لربط العمليات بالمحفظة عبر الجلسات. */
export const BANK_WALLET_ID = 'wallet-bank'
export const CASH_WALLET_ID = 'wallet-cash'

export interface SeedWalletsOutcome {
  seeded: boolean
  wallets: Wallet[]
  reason: string
}

/**
 * @param ownerDefaults أرصدة المالك المعتمدة. `false` لأي مستخدم آخر،
 *   فيبدأ بمحافظ رصيدها صفر ويعدّلها بنفسه.
 */
export function makeSeedWallets(deps: { wallets: WalletRepository }) {
  return async function seed(ownerDefaults: boolean): Promise<SeedWalletsOutcome> {
    const existing = await deps.wallets.listAll()
    if (existing.length > 0) {
      return {
        seeded: false,
        wallets: existing,
        reason: 'المحافظ موجودة قبل كده. مش هنكتب فوق تعديلاتك.',
      }
    }

    const bank: Wallet = {
      id: BANK_WALLET_ID,
      name: 'الراجحي',
      currency: 'SAR',
      kind: 'bank',
      openingBalanceMinor: ownerDefaults ? parseMoney('4837.83') : 0,
      openingAt: ownerDefaults ? '2025-01-01' : new Date().toISOString().slice(0, 10),
    }

    const cash: Wallet = {
      id: CASH_WALLET_ID,
      name: 'كاش',
      currency: 'SAR',
      kind: 'cash',
      openingBalanceMinor: ownerDefaults ? parseMoney('1000.00') : 0,
      openingAt: ownerDefaults ? '2026-09-06' : new Date().toISOString().slice(0, 10),
    }

    await deps.wallets.save(bank)
    await deps.wallets.save(cash)

    return {
      seeded: true,
      wallets: [bank, cash],
      reason: ownerDefaults
        ? 'اتزرعت محفظتان بالأرصدة المعتمدة في OVERRIDES §6.'
        : 'اتزرعت محفظتان برصيد صفر — عدّل الرصيد الافتتاحي من الإعدادات.',
    }
  }
}
