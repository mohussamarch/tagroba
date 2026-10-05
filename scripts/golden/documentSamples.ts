/**
 * كيانات وهمية لكل مجموعات الحساب الـ24 — مدخل `documentsGolden.ts`. ⚠️ **بيانات مخترعة بالكامل** (المستودع عام).
 * لكل مجموعة: نسخة **كاملة** (كل الحقول الاختيارية موجودة) ونسخة **ناقصة** (الاختياري غايب والفاضي `null`)،
 * عشان يبان إيه اللي بيتخزن `null` وإيه اللي ما بيتكتبش خالص. ونصوص فيها رقم حساب طويل عشان قص الأرقام يبان.
 */
import type {
  Wallet, Category, Merchant, ClassificationRule, Person, Tag, ImportBatch, Transaction, Obligation,
  PersonAllocation, Settlement, SourceRecord, TransactionTag,
} from '../../src/domain/entities/types'
import type { Asset, AssetLot, AssetSale, AssetPrice } from '../../src/domain/entities/assets'
import type { Budget, CategoryBudget } from '../../src/domain/entities/budgetEntities'
import type { RecurringItem } from '../../src/domain/entities/recurring'
import type { NotificationReceipt } from '../../src/domain/notifications'
import type { Project, ProjectLink, ProjectRule } from '../../src/domain/entities/projectEntities'

export interface Samples {
  wallets: Wallet[]; categories: Category[]; merchants: Merchant[]; rules: ClassificationRule[]; people: Person[]
  assets: Asset[]; tags: Tag[]; budgets: Budget[]; recurringItems: RecurringItem[]; importBatches: ImportBatch[]
  transactions: Transaction[]; obligations: Obligation[]; allocations: PersonAllocation[]; settlements: Settlement[]
  sourceRecords: SourceRecord[]; transactionTags: TransactionTag[]; categoryBudgets: CategoryBudget[]
  assetLots: AssetLot[]; assetSales: AssetSale[]; assetPrices: AssetPrice[]; notificationReceipts: NotificationReceipt[]
  projects: Project[]; projectLinks: ProjectLink[]; projectRules: ProjectRule[]
}

const NOW = '2026-09-30T08:00:00.000Z'
/** رقم حساب وهمي طويل — لازم يتقص لآخر 4 في الحقول النصية الحرة (OVERRIDES §2). */
const FAKE_ACCOUNT = '9988776655443322'

export function documentSamples(): Samples {
  return {
    wallets: [
      { id: 'w-bank', name: 'البنك الوهمي', currency: 'SAR', kind: 'bank', openingBalanceMinor: 123_456, openingAt: '2025-01-01', accountLast4: '4321' },
      { id: 'w-cash', name: 'كاش', currency: 'SAR', kind: 'cash', openingBalanceMinor: -500, openingAt: '2026-09-06' },
    ],
    categories: [
      { id: 'c-food', parentId: null, name: 'أكل', iconKey: 'utensils', lightColor: '#AA3344', darkColor: '#FF8899', active: true, order: 1, groupKey: 'food', requires: 'hasCar', noCarName: 'مواصلات', noCarIconKey: 'bus' },
      { id: 'c-cafe', parentId: 'c-food', name: 'قهوة', iconKey: 'coffee', lightColor: '#112233', darkColor: '#445566', active: false, order: 12 },
    ],
    merchants: [
      { id: 'm-1', displayName: 'متجر وهمي', normalizedName: 'متجر وهمي', aliases: ['TEST MART', 'تست مارت'], logoAsset: 'logos/test.png', logoSource: 'manual', verifiedCategoryId: 'c-food' },
      { id: 'm-2', displayName: 'Test Cafe', normalizedName: 'test cafe' },
    ],
    rules: [
      { id: 'r-1', priority: 10, matchText: 'test mart', matchMode: 'contains', categoryId: 'c-food', enabled: true },
      { id: 'r-2', priority: 0, matchText: 'cafe', matchMode: 'exact', categoryId: 'c-cafe', enabled: false },
    ],
    people: [
      { id: 'p-1', name: 'شخص وهمي', archived: false },
      { id: 'p-2', name: 'Test Person', archived: true },
    ],
    assets: [
      { id: 'a-gold', name: 'ذهب وهمي', kind: 'gold', unitLabel: 'جرام', currency: 'SAR', archived: false, feedSymbol: 'XAU', note: 'ملاحظة' },
      { id: 'a-fund', name: 'صندوق وهمي', kind: 'fund', unitLabel: 'وحدة', currency: 'SAR', archived: true },
    ],
    tags: [
      { id: 'tag-1', normalizedName: 'سفر', displayName: 'سفر' },
      { id: 'tag-2', normalizedName: 'trip 2026', displayName: 'Trip 2026' },
    ],
    budgets: [
      { id: '2026-09', periodKey: '2026-09', periodStart: '2026-08-28', periodEnd: '2026-09-27', totalLimitMinor: 800_000, thresholdPercent: 80, createdAt: NOW, updatedAt: NOW },
      { id: '2026-10', periodKey: '2026-10', periodStart: '2026-09-28', periodEnd: '2026-10-27', totalLimitMinor: null, thresholdPercent: null, createdAt: NOW, updatedAt: NOW },
    ],
    recurringItems: [
      { id: 'rec-1', name: 'اشتراك وهمي', merchantKey: 'id:m-2', kind: 'subscription', cycleMonths: 1, expectedMinor: 4_500, currency: 'SAR', nextDueAt: '2026-10-05', active: true, confirmed: true },
      { id: 'rec-2', name: 'فاتورة وهمية', merchantKey: 'manual:bill', kind: 'bill', cycleMonths: 12, expectedMinor: 120_000, currency: 'SAR', nextDueAt: '2027-01-01', active: false, confirmed: false },
    ],
    importBatches: [
      { id: 'b-1', sourceType: 'csv_preview', fileHash: 'hash-1', fileName: `statement-${FAKE_ACCOUNT}.csv`, importedAt: NOW, state: 'committed', counts: { total: 10, imported: 7, duplicates: 1, similar: 1, conflicts: 0, invalid: 1 } },
      { id: 'b-2', sourceType: 'sms', fileHash: 'hash-2', fileName: 'sms', importedAt: NOW, state: 'staged', counts: { total: 0, imported: 0, duplicates: 0, similar: 0, conflicts: 0, invalid: 0 } },
    ],
    transactions: [
      {
        id: 't-full', occurredAt: '2026-09-15', datePrecision: 'day', sourceTime: '14:05', sourceOrder: 3, economicKind: 'purchase', economicKindConfirmed: true,
        observedDirection: 'out', amountMinor: 9_007_199_254_740_991, originalAmountMinor: 10_000, currency: 'SAR', merchantId: 'm-1', categoryId: 'c-food',
        categoryConfirmed: true, excludedFromBudget: true, reviewState: 'confirmed', note: `ملاحظة فيها رقم ${FAKE_ACCOUNT}`, walletId: 'w-bank', transferToWalletId: 'w-cash',
        statedBalanceMinor: -2_500, rawDescription: `شراء من حساب ${FAKE_ACCOUNT}`, rawMerchantName: 'TEST MART', isCashTagged: true, sourceCategory: 'مشتريات',
        sourceOperationType: 'POS', createdAt: NOW, updatedAt: NOW,
      },
      {
        id: 't-min', occurredAt: '2026-09-01', datePrecision: 'minute', sourceOrder: 0, economicKind: 'unclassified', economicKindConfirmed: false, observedDirection: 'in',
        amountMinor: 0, currency: 'SAR', categoryConfirmed: false, excludedFromBudget: false, reviewState: 'needs_review', isCashTagged: false, createdAt: NOW, updatedAt: NOW,
      },
    ],
    obligations: [
      { id: 'o-1', personId: 'p-1', originTransactionId: 't-full', kind: 'receivable', originalMinor: 50_000, currency: 'SAR' },
      { id: 'o-2', personId: 'p-2', originTransactionId: null, kind: 'custody_payable', originalMinor: 1, currency: 'SAR' },
    ],
    allocations: [
      { id: 'al-1', transactionId: 't-full', personId: 'p-1', allocationKind: 'receivable', amountMinor: 50_000, currency: 'SAR' },
      { id: 'al-2', transactionId: 't-full', personId: 'p-2', allocationKind: 'gift', amountMinor: 1_000, currency: 'SAR' },
    ],
    settlements: [
      { id: 's-1', transactionId: 't-min', obligationId: 'o-1', amountMinor: 20_000 },
    ],
    sourceRecords: [
      { id: 'sr-1', batchId: 'b-1', accountIdentity: `acct-${FAKE_ACCOUNT}`, sourceReference: `ref-${FAKE_ACCOUNT}`, sourceHash: 'sh-1', originalRowIndex: 4, rawLine: `2026-09-15,شراء,${FAKE_ACCOUNT},100.00`, transactionId: 't-full', matchingState: 'new', reason: 'جديد' },
      { id: 'sr-2', batchId: 'b-1', accountIdentity: 'acct-****3322', sourceReference: null, sourceHash: 'sh-2', originalRowIndex: 0, rawLine: '', transactionId: null, matchingState: 'invalid', reason: 'صف غير صالح' },
    ],
    transactionTags: [
      { id: 'tt-1', transactionId: 't-full', tagId: 'tag-1' },
    ],
    categoryBudgets: [
      { id: 'cb-1', budgetId: '2026-09', categoryId: 'c-food', limitMinor: 150_000, notifyEnabled: true, thresholdPercent: 90 },
      { id: 'cb-2', budgetId: '2026-09', categoryId: 'c-cafe', limitMinor: 30_000, notifyEnabled: false, thresholdPercent: null },
    ],
    assetLots: [
      { id: 'lot-1', assetId: 'a-gold', purchasedAt: '2026-01-10', quantity: 1_250_000_000, principalMinor: 300_000, feeMinor: 1_500, transactionId: 't-full' },
      { id: 'lot-2', assetId: 'a-fund', purchasedAt: '2026-02-01', quantity: 1, principalMinor: 100, feeMinor: 0 },
    ],
    assetSales: [
      { id: 'sale-1', assetId: 'a-gold', soldAt: '2026-06-01', quantity: 250_000_000, grossProceedsMinor: 70_000, feeMinor: 200, transactionId: 't-min' },
      { id: 'sale-2', assetId: 'a-gold', soldAt: '2026-07-01', quantity: 100_000_000, grossProceedsMinor: 28_000, feeMinor: 0 },
    ],
    assetPrices: [
      { assetId: 'a-gold', pricePerUnitMinor: 28_550, asOf: '2026-09-29', source: 'feed' },
      { assetId: 'a-fund', pricePerUnitMinor: 1_050, asOf: '2026-09-01', source: 'manual' },
    ],
    notificationReceipts: [
      { eventKey: 'budget|2026-08-28|total|80', threshold: 80, periodStart: '2026-08-28', sentAt: NOW, categoryId: 'c-food', recurringId: 'rec-1' },
      { eventKey: 'recurring|2026-08-28|rec-2', threshold: null, periodStart: '2026-08-28', sentAt: NOW },
    ],
    projects: [
      { id: 'pr-1', name: 'فرح وهمي', normalizedName: 'فرح وهمي', archived: false, createdAt: NOW },
      { id: 'pr-2', name: 'Trip', normalizedName: 'trip', archived: true, createdAt: NOW },
    ],
    projectLinks: [
      { id: 'pl-1', projectId: 'pr-1', transactionId: 't-full', source: 'manual', createdAt: NOW },
      { id: 'pl-2', projectId: 'pr-1', transactionId: 't-min', source: 'excluded', createdAt: NOW },
    ],
    projectRules: [
      { id: 'prr-1', projectId: 'pr-1', matchText: 'قاعة', matchMode: 'startsWith', direction: 'out', enabled: true, createdAt: NOW },
      { id: 'prr-2', projectId: 'pr-2', matchText: 'hotel', matchMode: 'contains', direction: 'any', enabled: false, createdAt: NOW },
    ],
  }
}
