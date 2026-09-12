import { describe, it, expect } from 'vitest'
import { makeImportStatement, type ImportRequest } from '../../src/application/useCases/importStatement'
import {
  MemoryTransactionRepository, MemorySourceRecordRepository, MemoryImportBatchRepository, MemoryCategoryRepository,
  MemoryMerchantRepository, MemoryRuleRepository, PassthroughUnitOfWork, SequentialIdGenerator, FixedClock,
} from '../../src/infrastructure/memory/memoryRepositories'
import type { ParsedRow } from '../../src/infrastructure/import/schemas'
import { normalizeText } from '../../src/domain/normalize'

/**
 * بلاغ 2026-09-11 (HANDOVER §27–29): نفس كشف الراجحي اتستورد مرتين. اسم التاجر اتقرا
 * مختلف بين نسختين من القارئ، ونص نسخة منهما بأشكال العرض العربية، والمرجع فاضي ⇒
 * منع التكرار (المبني على اسم التاجر) سجّل الكشف كله «جديد» مرة تانية.
 */

function system() {
  return makeImportStatement({
    txns: new MemoryTransactionRepository(), sources: new MemorySourceRecordRepository(),
    batches: new MemoryImportBatchRepository(), categories: new MemoryCategoryRepository(),
    merchants: new MemoryMerchantRepository(), rules: new MemoryRuleRepository(), uow: new PassthroughUnitOfWork(),
    ids: new SequentialIdGenerator(), clock: new FixedClock('2026-09-11T00:00:00Z'),
  })
}

/** ثلاث سطور كشف: تحويل، وشراءان حقيقيان بنفس اليوم والمبلغ (الرصيد بعدهما مختلف). */
function statementRows(names: [string, string, string], description: string, balances = true): ParsedRow[] {
  const base = { reference: null, sourceName: 'الراجحي', description, direction: 'out' as const }
  return [
    { ...base, lineNumber: 1, date: '2026-08-25', amountMinor: 212500, merchantName: names[0], raw: 'r1', ...(balances ? { statedBalanceMinor: 500000 } : {}) },
    { ...base, lineNumber: 2, date: '2026-08-26', amountMinor: 2999, merchantName: names[1], raw: 'r2', ...(balances ? { statedBalanceMinor: 497001 } : {}) },
    { ...base, lineNumber: 3, date: '2026-08-26', amountMinor: 2999, merchantName: names[2], raw: 'r3', ...(balances ? { statedBalanceMinor: 494002 } : {}) },
  ]
}

function request(fileName: string, rows: ParsedRow[]): ImportRequest {
  return { fileName, content: fileName, parsedRows: rows, sourceType: 'pdf_alrajhi', accountIdentity: 'الراجحي', walletId: 'wallet-bank' }
}

describe('إعادة استيراد نفس الكشف بقراءة مختلفة', () => {
  it('الكشف الأول: شراءان حقيقيان بنفس اليوم والمبلغ يتسجلوا الاتنين', async () => {
    const imports = system()
    const first = request('first.pdf', statementRows(['تحويل صادر • …4599', 'ALBAIK', 'ALBAIK'], 'ALBaik, (****1234-****5678):ملاحظة'))
    const preview = await imports.preview(first)
    expect(preview.counts.newCount).toBe(3)
  })

  it('نفس السطور تاني باسم تاجر مختلف ونص بأشكال العرض ⇒ كلها «متسجلة قبل كده» ولا يُضاف شيء', async () => {
    const imports = system()
    const first = request('first.pdf', statementRows(['تحويل صادر • …4599', 'ALBAIK', 'ALBAIK'], 'ALBaik, (****1234-****5678):ﺔﻈﺣﻼﻣ'))
    await imports.commit(first, await imports.preview(first))

    const second = request('second.pdf', statementRows(
      ['عملية تحويل داخلية', 'شراء عبر نقاط البيع -سامسونج', 'شراء عبر نقاط البيع -سامسونج'], 'ALBaik, (****1234-****5678):ملاحظة'))
    const preview = await imports.preview(second)
    expect(preview.lines.map((l) => l.state)).toEqual(['duplicate', 'duplicate', 'duplicate'])
    expect(preview.counts.newCount).toBe(0)
    expect(preview.lines[0].reason).toContain('الرصيد')
  })

  it('من غير رصيد معلن لا يُحكم بالتكرار من اليوم والمبلغ وحدهما (لا حذف لعملية حقيقية)', async () => {
    const imports = system()
    const first = request('first.pdf', statementRows(['A', 'B', 'C'], 'x', false))
    await imports.commit(first, await imports.preview(first))
    const preview = await imports.preview(request('second.pdf', statementRows(['X', 'Y', 'Z'], 'x', false)))
    expect(preview.lines.every((l) => l.state !== 'duplicate')).toBe(true)
  })

  /*
   * بلاغ 2026-09-12 على بيانات المالك: كشف الراجحي بيطبع **رصيد نهاية اليوم**
   * على كل سطور اليوم، فعمليتين حقيقيتين بنفس اليوم والمبلغ والاتجاه بيبقى
   * ليهم نفس مفتاح الرصيد. فهرس التكرار كان بيخزّن سجلًا واحدًا لكل مفتاح،
   * فالاتنين كانوا يتعلّموا «مكرر» وتضيع عملية حقيقية في صمت.
   */
  it('صفّان بنفس رصيد اليوم مقابل عملية واحدة موجودة ⇒ واحد مكرر والتاني حقيقي', async () => {
    const imports = system()
    const base = {
      reference: null, sourceName: 'الراجحي', description: 'قهوة', direction: 'out' as const,
      date: '2026-08-26', amountMinor: 500, statedBalanceMinor: 400000,
    }
    // الموجود: عملية واحدة بس
    const first = request('first.pdf', [{ ...base, lineNumber: 1, merchantName: 'CAFE', raw: 'r1' }])
    await imports.commit(first, await imports.preview(first))

    // الوارد: نفس اليوم والمبلغ والرصيد مرتين — قهوتين حقيقيتين
    const second = request('second.pdf', [
      { ...base, lineNumber: 1, merchantName: 'CAFE', raw: 'r1' },
      { ...base, lineNumber: 2, merchantName: 'CAFE', raw: 'r2' },
    ])
    const preview = await imports.preview(second)
    expect(preview.lines[0].state).toBe('duplicate')
    expect(preview.lines[1].state).not.toBe('duplicate')
    expect(preview.counts.duplicates).toBe(1)
  })

  /*
   * نفس البلاغ: الكشف اتستورد مرتين قبل كده، فالعملية الواحدة بقى ليها أكتر
   * من `sourceRecord`. فهرس التكرار كان بيتبني من **سجلات المصدر**، فالعملية
   * كانت تتعد مرتين وتبتلع صفين واردين — وعملية حقيقية تتحسب «مكرر».
   */
  it('عملية ليها سجلَّي مصدر تتعد مرة واحدة — ما تبتلعش صفين', async () => {
    const sources = new MemorySourceRecordRepository()
    const imports = makeImportStatement({
      txns: new MemoryTransactionRepository(), sources,
      batches: new MemoryImportBatchRepository(), categories: new MemoryCategoryRepository(),
      merchants: new MemoryMerchantRepository(), rules: new MemoryRuleRepository(),
      uow: new PassthroughUnitOfWork(), ids: new SequentialIdGenerator(),
      clock: new FixedClock('2026-09-11T00:00:00Z'),
    })
    const base = {
      reference: null, sourceName: 'الراجحي', description: 'قهوة', direction: 'out' as const,
      date: '2026-08-26', amountMinor: 500, statedBalanceMinor: 400000,
    }
    const first = request('first.pdf', [{ ...base, lineNumber: 1, merchantName: 'CAFE', raw: 'r1' }])
    await imports.commit(first, await imports.preview(first))

    // سجل مصدر تاني لنفس العملية — زي ما بيحصل لما نفس الكشف يتستورد مرتين
    const records = await sources.listByAccountIdentity('الراجحي')
    expect(records).toHaveLength(1)
    await sources.saveMany([{ ...records[0], id: 'source-record-2' }])

    const second = request('second.pdf', [
      { ...base, lineNumber: 1, merchantName: 'CAFE', raw: 'r1' },
      { ...base, lineNumber: 2, merchantName: 'CAFE', raw: 'r2' },
    ])
    const preview = await imports.preview(second)
    expect(preview.counts.duplicates).toBe(1)
    expect(preview.lines[1].state).not.toBe('duplicate')
  })

  /*
   * بلاغ 2026-09-12: نفس الملف اتستورد منه 14 صف قبل كده، وبعدين اتستورد
   * كامل (1912 صف). الحارس القديم كان بيرجّع الدفعة القديمة ويعتبر نفسه نجح
   * **من غير ما يكتب ولا عملية** — 1077 عملية حقيقية ضاعت في صمت.
   * قرار المالك: يكمل طول ما فيه جديد؛ منع التكرار بيشتغل صف بصف.
   */
  it('ملف اتستورد منه جزء قبل كده ⇒ الصفوف الجديدة تتضاف مش تترفض', async () => {
    const txns = new MemoryTransactionRepository()
    const imports = makeImportStatement({
      txns, sources: new MemorySourceRecordRepository(),
      batches: new MemoryImportBatchRepository(), categories: new MemoryCategoryRepository(),
      merchants: new MemoryMerchantRepository(), rules: new MemoryRuleRepository(),
      uow: new PassthroughUnitOfWork(), ids: new SequentialIdGenerator(),
      clock: new FixedClock('2026-09-12T00:00:00Z'),
    })
    const rows = statementRows(['A', 'B', 'C'], 'وصف')

    // نفس اسم الملف ⇒ نفس البصمة. الأول: صف واحد بس
    const partial = request('same.pdf', [rows[0]])
    await imports.commit(partial, await imports.preview(partial))

    // بعدين: نفس الملف كامل — المفروض الصفين الجداد يتضافوا
    const full = request('same.pdf', rows)
    const preview = await imports.preview(full)
    expect(preview.previousBatch).not.toBeNull()
    expect(preview.counts.newCount).toBe(2)

    const committed = await imports.commit(full, preview)
    expect(committed.counts.imported).toBe(2)
    expect((await txns.listByDateRange('2026-08-01', '2026-08-31')).length).toBe(3)
  })

  it('توحيد النص يطبّع أشكال العرض العربية بالترتيب المنطقي (NFKC)', () => {
    expect(normalizeText('ﻣﻼﺣﻈﺔ')).toBe(normalizeText('ملاحظة'))
    expect(normalizeText('ﻣﺤﻤﺪ')).toBe(normalizeText('محمد'))
  })

  it('نص مخزن بالترتيب البصري المعكوس (دفعة 2026-09-07) لا يُعكس آليًا — HANDOVER §9.5', () => {
    // «ﺔﻈﺣﻼﻣ» = «ملاحظة» مقلوبة. العكس الآلي يفسد النص السليم، فالتكرار يُكتشف بالرصيد لا بالنص.
    expect(normalizeText('ﺔﻈﺣﻼﻣ')).not.toBe(normalizeText('ملاحظة'))
  })
})
