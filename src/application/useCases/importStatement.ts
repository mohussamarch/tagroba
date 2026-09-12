import { hashContent, importFingerprint } from '../../domain/dedupe'
import type { Id, ImportBatch, SourceRecord, Transaction } from '../../domain/entities/types'
import { runPreview } from './importPreview'
import type {
  ImportPreview,
  ImportPreviewLine,
  ImportRequest,
  ImportStatementDeps,
} from './importTypes'

export type {
  ImportPreview,
  ImportPreviewLine,
  ImportRequest,
  ImportStatementDeps,
} from './importTypes'

/**
 * ImportStatement — قراءة الكشف، منع التكرار، إنشاء الدفعة.
 *
 * التدفق مرحلتان صراحةً (spec/04):
 *   preview()  → استخراج + مراجعة + ملخص الأثر، **بلا كتابة** (importPreview.ts)
 *   commit()   → تأكيد ذري داخل UnitOfWork
 *
 * السبب في الفصل: «إعادة الضغط أو إعادة فتح التطبيق لا تعيد الالتزام بالدفعة»
 * و«صفر أو كامل الدفعة، لا نصفها» (spec/05 و spec/06).
 */
export function makeImportStatement(deps: ImportStatementDeps) {
  async function preview(request: ImportRequest): Promise<ImportPreview> {
    return runPreview(deps, request)
  }

  function buildTransaction(
    id: Id,
    line: ImportPreviewLine,
    now: string,
    request: ImportRequest,
  ): Transaction {
    const txn: Transaction = {
      id,
      occurredAt: line.row.date,
      datePrecision: 'day',
      sourceOrder: line.row.lineNumber,
      // ⚠️ النوع الاقتصادي **لا يُستنتج من اتجاه السيولة** (spec/02).
      // يبدأ غير محدد وينتظر تأكيد المستخدم في المراجعة.
      economicKind: 'unclassified',
      economicKindConfirmed: false,
      observedDirection: line.row.direction,
      amountMinor: line.row.amountMinor,
      currency: 'SAR',
      categoryConfirmed: false,
      excludedFromBudget: false,
      reviewState: line.categoryId ? 'suggested' : 'needs_review',
      isCashTagged: false,
      rawDescription: line.row.description,
      rawMerchantName: line.row.merchantName,
      createdAt: now,
      updatedAt: now,
    }
    if (line.categoryId) txn.categoryId = line.categoryId
    // الرصيد المعلن يُحفظ كما ورد — هو مرجع المطابقة (OVERRIDES §7-ب)
    if (line.row.statedBalanceMinor !== undefined) {
      txn.statedBalanceMinor = line.row.statedBalanceMinor
    }
    if (request.walletId) txn.walletId = request.walletId
    if (line.row.sourceCategory) txn.sourceCategory = line.row.sourceCategory
    if (line.row.sourceOperationType) txn.sourceOperationType = line.row.sourceOperationType
    return txn
  }

  /**
   * المرحلة الثانية: الالتزام الذري.
   * `selectedLineNumbers` تسمح بتضمين متشابه أو استبعاد جديد **بقرار صريح**.
   */
  async function commitPrepared(
    request: ImportRequest,
    previewResult: ImportPreview,
    selectedLineNumbers?: readonly number[],
  ): Promise<ImportBatch> {
    const selection = selectedLineNumbers
      ? new Set(selectedLineNumbers)
      : new Set(previewResult.lines.filter((l) => l.selectedByDefault).map((l) => l.row.lineNumber))

    // التعارض لا يُكتب أبدًا بلا حسم صريح — لا استبدال صامت (spec/05)
    for (const line of previewResult.lines) {
      if (selection.has(line.row.lineNumber) && line.state === 'conflict') {
        throw new Error(`الصف ${line.row.lineNumber} فيه تعارض ولازم تحسمه الأول. ${line.reason}`)
      }
    }

    return deps.uow.run(async () => {
      const batchId = deps.ids.next('batch')
      const now = deps.clock.nowIso()
      const transactions: Transaction[] = []
      const records: SourceRecord[] = []

      const counts = {
        total: previewResult.counts.total,
        imported: 0, // يُملأ بعد البناء
        duplicates: previewResult.counts.duplicates,
        similar: previewResult.counts.similar,
        conflicts: previewResult.counts.conflicts,
        invalid: previewResult.counts.invalid,
      }

      for (const line of previewResult.lines) {
        const included = selection.has(line.row.lineNumber)
        const txnId = included ? deps.ids.next('txn') : null
        if (txnId) transactions.push(buildTransaction(txnId, line, now, request))

        records.push({
          id: deps.ids.next('src'),
          batchId,
          accountIdentity: request.accountIdentity,
          sourceReference: line.row.reference,
          sourceHash: hashContent(line.row.raw),
          originalRowIndex: line.row.lineNumber,
          rawLine: line.row.raw,
          transactionId: txnId,
          matchingState: line.state,
          reason: line.reason,
        })
      }

      counts.imported = transactions.length

      /*
       * بروتوكول علامة الالتزام — يحقق «صفر أو كامل الدفعة» (spec/06)
       * حتى على Firestore حيث لا تغطي المعاملة الواحدة مئات المستندات.
       *
       *   ١. الدفعة تُكتب `staged` **قبل** أي شيء
       *   ٢. **سجلات المصدر أولًا** ثم العمليات (الانقطاع المحتمل هنا)
       *   ٣. التحويل إلى `committed` بكتابة واحدة ذرّية بطبيعتها
       *
       * ⚠️ **ترتيب الخطوة ٢ ليس تفصيلًا.** سجل المصدر هو الفهرس الوحيد
       * الذي يربط العملية بدفعتها. لو كُتبت العمليات أولًا وانقطع الاتصال
       * قبل السجلات، تصير العمليات **يتيمة**: موجودة في قاعدة البيانات
       * ولا سبيل للوصول إليها من الدفعة، فيستحيل تنظيفها.
       *
       * بهذا الترتيب أي انقطاع يترك سجلات تُشير إلى عمليات قد لا تكون
       * كُتبت بعد — وحذف معرّف غير موجود لا يضر — فالتنظيف يعمل دائمًا.
       *
       * أي انقطاع يترك دفعة `staged`، و findByFileHash لا يرى إلا
       * `committed`، فما تحتها غير معتمد ويُنظَّف بـ ResumeStagedBatch.
       */
      const batch: ImportBatch = {
        id: batchId,
        sourceType: request.sourceType,
        fileHash: previewResult.fileHash,
        fileName: request.fileName,
        importedAt: now,
        state: 'staged',
        counts,
      }

      await deps.batches.save(batch) // ١
      await deps.sources.saveMany(records) // ٢ — الفهرس أولًا
      await deps.txns.saveMany(transactions) //     ثم العمليات
      await deps.batches.updateState(batchId, 'committed') // ٣

      return { ...batch, state: 'committed' }
    })
  }


  let committing = false
  async function commit(request:ImportRequest, previous:ImportPreview, selected?:readonly number[]):Promise<ImportBatch> {
    if (committing) throw new Error('فيه استيراد بيتحفظ حاليًا؛ استنى اكتماله')
    committing = true
    try {
      if (importFingerprint(request.content, request.accountIdentity) !== previous.fileHash || request.accountIdentity !== previous.accountIdentity) {
        throw new Error('بيانات الاستيراد اتغيرت؛ اعمل معاينة جديدة')
      }
      const fresh = await preview(request)
      const selection = selected ?? previous.lines.filter(line=>line.selectedByDefault).map(line=>line.row.lineNumber)
      /*
       * الملف ده اتستورد قبل كده؟ نرجّع الدفعة القديمة **بس لو مفيش حاجة
       * جديدة تتضاف**. قرار المالك 2026-09-12: الناس بترفع ملفات كتير
       * متداخلة، والملف اللي اتستورد منه جزء لازم يكمل. منع التكرار بيشتغل
       * **صف بصف** أصلًا، فمفيش خطر تكرار من إكمال الملف.
       * (قبل كده: أي ملف بصمته متسجلة كان بيترفض بصمت — 1077 عملية حقيقية
       * ما دخلتش والورقة قفلت كأنها نجحت.)
       */
      const addable = selection.filter((number) => {
        const line = fresh.lines.find((l) => l.row.lineNumber === number)
        return line !== undefined && line.state !== 'duplicate' && line.state !== 'invalid'
      })
      if (fresh.previousBatch && addable.length === 0) return fresh.previousBatch
      for (const number of selection) {
        const before = previous.lines.find(line=>line.row.lineNumber===number)
        const now = fresh.lines.find(line=>line.row.lineNumber===number)
        if (!before || !now || JSON.stringify(before.row)!==JSON.stringify(now.row) || now.state!==before.state || now.matchedTransactionId!==before.matchedTransactionId) {
          throw new Error('فيه بيانات اتغيرت بعد المعاينة؛ اعمل معاينة جديدة علشان نمنع التكرار')
        }
        if (now.state === 'duplicate' || now.state === 'invalid') throw new Error('العملية المكررة أو غير الصالحة مش قابلة للإضافة')
      }
      return await commitPrepared(request, fresh, selection)
    } finally { committing = false }
  }

  return { preview, commit }
}
