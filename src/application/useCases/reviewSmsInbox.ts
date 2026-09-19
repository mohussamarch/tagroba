import { rememberMerchant } from '../../domain/merchantMemory'
import type { Category, Id, MatchingState, Transaction } from '../../domain/entities/types'
import type { SmsRow } from '../ports/BankSmsPort'
import type { CategoryRepository, IdGenerator, MerchantRepository } from '../ports/repositories'
import type { makeImportStatement, ImportPreview, ImportRequest } from './importStatement'
import type { makeManageSmsInbox } from './manageSmsInbox'

/**
 * شاشة رسايل البنك — OVERRIDES §36 (قرار المالك 2026-09-19):
 * قايمة واحدة فيها كل عملية بتصنيفها المقترح، وزرار واحد «سجّل الكل». مفيش حاجة بتتسجل من غير ضغطة.
 * منع التكرار والتصنيف هما نفس مسار استيراد الكشف (`importStatement`) — الحالة دي بس بتجمّعهم للشاشة.
 */

export interface SmsReviewLine {
  messageId: string
  lineNumber: number
  date: string
  merchant: string
  amountMinor: number
  direction: 'in' | 'out'
  categoryId?: Id
  /** المستخدم افتكر المحل ده قبل كده — مفيش سؤال «نفتكره؟» تاني. */
  remembered: boolean
  state: MatchingState
  reason: string
}

export interface SmsReview {
  enabled: boolean
  permission: boolean
  senders: string[]
  more: boolean
  /** جديدة — بتتسجل بـ«سجّل الكل». */
  ready: SmsReviewLine[]
  /** شبه عملية موجودة — ما بتتسجلش إلا لو المستخدم اختارها. التعارض ما بيتسجلش أبدًا. */
  similar: SmsReviewLine[]
  /** موجودة فعلًا (من كشف أو رسالة قبل كده) — بتتشال من القايمة مع «سجّل الكل». */
  duplicates: SmsReviewLine[]
  failed: { messageId: string; sender: string; date: string; reason: string }[]
  /** كل التصنيفات (حتى المخفية) عشان اسم ولون تصنيف قديم يبان؛ الاختيار من الظاهر بس. */
  categories: Category[]
}

export interface SmsReviewTarget {
  walletId: Id
  /** اسم المحفظة — نطاق تفرّد المرجع، زي شاشة الاستيراد. */
  accountIdentity: string
}

export interface ReviewSmsInboxDeps {
  inbox: ReturnType<typeof makeManageSmsInbox>
  importer: ReturnType<typeof makeImportStatement>
  merchants: MerchantRepository
  categories: CategoryRepository
  ids: IdGenerator
  /** رفع المحل كاقتراح للقايمة المشتركة (OVERRIDES §25). فشله ما يوقفش الحفظ على الجهاز. */
  contribute?: (transaction: Pick<Transaction, 'economicKind' | 'observedDirection' | 'rawMerchantName'>, categoryId: Id) => Promise<unknown>
}

type Inbox = Awaited<ReturnType<ReviewSmsInboxDeps['inbox']['refresh']>>

export function makeReviewSmsInbox(deps: ReviewSmsInboxDeps) {
  let session: { request: ImportRequest; preview: ImportPreview; messageByLine: Map<number, string> } | null = null

  async function build(inbox: Inbox, target: SmsReviewTarget): Promise<SmsReview> {
    const rows: SmsRow[] = []
    const messageByLine = new Map<number, string>()
    const failed: SmsReview['failed'] = []
    for (const item of inbox.items) {
      if (item.parsed.ok) {
        rows.push(item.parsed.row)
        messageByLine.set(item.parsed.row.lineNumber, item.id)
      } else failed.push({ messageId: item.id, sender: item.sender, date: item.receivedAt.slice(0, 10), reason: item.parsed.reason })
    }
    const categories = await deps.categories.listAll()
    const base = { enabled: inbox.enabled, permission: inbox.permission, senders: inbox.senders, more: inbox.more, failed, categories }
    session = null
    if (!rows.length) return { ...base, ready: [], similar: [], duplicates: [] }

    const request: ImportRequest = {
      fileName: 'bank-sms.json',
      content: JSON.stringify(rows),
      accountIdentity: target.accountIdentity,
      sourceType: 'sms',
      walletId: target.walletId,
      parsedRows: rows,
      schema: 'sms',
    }
    const preview = await deps.importer.preview(request)
    session = { request, preview, messageByLine }
    const lines: SmsReviewLine[] = preview.lines.map((line) => {
      const out: SmsReviewLine = {
        messageId: messageByLine.get(line.row.lineNumber)!,
        lineNumber: line.row.lineNumber,
        date: line.row.date,
        merchant: line.row.merchantName?.trim() || '',
        amountMinor: line.row.amountMinor,
        direction: line.row.direction,
        remembered: line.categorySource === 'verified_merchant',
        state: line.state,
        reason: line.reason,
      }
      if (line.categoryId) out.categoryId = line.categoryId
      return out
    })
    const newestFirst = (a: SmsReviewLine, b: SmsReviewLine) => b.date.localeCompare(a.date) || b.lineNumber - a.lineNumber
    return {
      ...base,
      ready: lines.filter((l) => l.state === 'new').sort(newestFirst),
      similar: lines.filter((l) => l.state === 'similar' || l.state === 'conflict').sort(newestFirst),
      duplicates: lines.filter((l) => l.state === 'duplicate'),
    }
  }

  return {
    available: deps.inbox.available,
    async load(target: SmsReviewTarget) { return build(await deps.inbox.refresh(), target) },
    async enable(senders: string[], target: SmsReviewTarget) { return build(await deps.inbox.enable(senders), target) },
    async disable(target: SmsReviewTarget) { return build(await deps.inbox.disable(), target) },
    async dismiss(messageIds: string[], target: SmsReviewTarget) { return build(await deps.inbox.dismiss(messageIds), target) },

    /**
     * «سجّل الكل»: الجديد كله + الشبيه اللي المستخدم اختاره. التصنيف اللي اختاره المستخدم بيتحفظ مؤكد.
     * الرسايل اللي اتسجلت والمكررة بتتشال من القايمة؛ الباقي (المرفوض والشبيه اللي ما اتختارش) بيفضل.
     */
    async recordAll(choices: { categories: ReadonlyMap<number, Id>; includeSimilar: readonly number[] }): Promise<{ recorded: number }> {
      if (!session) return { recorded: 0 }
      const { request, preview, messageByLine } = session
      const allowed = new Set(choices.includeSimilar)
      const selection = preview.lines
        .filter((l) => l.state === 'new' || (l.state === 'similar' && allowed.has(l.row.lineNumber)))
        .map((l) => l.row.lineNumber)
      let recorded = 0
      if (selection.length) {
        const batch = await deps.importer.commit(request, preview, selection, choices.categories)
        if (!(preview.previousBatch && batch.id === preview.previousBatch.id)) recorded = selection.length
      }
      const lines = recorded ? selection : []
      const duplicates = preview.lines.filter((l) => l.state === 'duplicate').map((l) => l.row.lineNumber)
      const done = [...lines, ...duplicates]
      await deps.inbox.imported(done.map((lineNumber) => ({ id: messageByLine.get(lineNumber)!, lineNumber })), done)
      session = null
      return { recorded }
    },

    /** «أيوه افتكره»: تصنيف المحل بيتثبت على الجهاز، ويترفع اقتراح للقايمة المشتركة لو مصروف. */
    async remember(merchantName: string, categoryId: Id, direction: 'in' | 'out'): Promise<boolean> {
      const merchant = rememberMerchant(await deps.merchants.listAll(), merchantName, categoryId, deps.ids.next('merchant'))
      if (!merchant) return false
      await deps.merchants.saveMany([merchant])
      if (direction === 'out' && deps.contribute) {
        await deps.contribute({ economicKind: 'purchase', observedDirection: 'out', rawMerchantName: merchantName }, categoryId).catch(() => null)
      }
      return true
    },
  }
}
