import {
  parseAlrajhiPdf,
  type AlrajhiPdfOutcome,
} from '../../infrastructure/import/pdf/alrajhiPdfSchema'
import { extractPdfPages, PdfReadError } from '../../infrastructure/import/pdf/pdfTextExtractor'
import type { ParsedRow } from '../../infrastructure/import/schemas'

/**
 * ReadPdfStatement — يحوّل كشف PDF لصفوف موحّدة.
 *
 * الناتج **نفس نوع** صفوف الـCSV (`ParsedRow`)، فبقية المنظومة
 * — منع التكرار والتصنيف والحفظ على مرحلتين — بتشتغل زي ما هي بالظبط.
 * مفيش خط استيراد موازي للـPDF، وده مقصود: خطان يعني قاعدتان
 * لمنع التكرار، وواحدة منهم هتتأخر عن التانية عاجلًا أو آجلًا.
 */

export { PdfReadError }

export interface PdfStatementResult {
  rows: ParsedRow[]
  errors: AlrajhiPdfOutcome['errors']
  pagesRead: number
  /**
   * نص قانوني يمثّل الكشف، بتتحسب منه بصمة الملف.
   *
   * ليه مش بايتات الـPDF نفسه؟ لأن الملف الواحد ممكن يتصدّر مرتين
   * فيطلع ببايتات مختلفة (وقت التوليد جوه الملف) وهو نفس الكشف.
   * البصمة على **محتوى العمليات** بتمسك التكرار الحقيقي:
   * نفس الكشف = نفس البصمة، حتى لو اتصدّر تاني.
   */
  content: string
}

/** يبني النص القانوني: سطر لكل عملية بالحقول اللي بتعرّفها. */
function canonicalContent(rows: readonly ParsedRow[]): string {
  return rows
    .map((r) => `${r.date}|${r.amountMinor}|${r.direction}|${r.statedBalanceMinor ?? ''}`)
    .join('\n')
}

export function makeReadPdfStatement() {
  return async function readPdfStatement(
    data: ArrayBuffer,
    onProgress?: (progress: { page: number; total: number }) => void,
  ): Promise<PdfStatementResult> {
    const pages = await extractPdfPages(data, onProgress)
    const outcome = parseAlrajhiPdf(pages)

    if (outcome.rows.length === 0) {
      // الفشل بيتقال بسببه — «مفيش عمليات» لوحدها بتخلي المستخدم تايه
      throw new PdfReadError(
        `قرينا ${outcome.pagesRead} صفحة بس ملقيناش أي عملية. ` +
          `القارئ متظبط على كشف حساب مصرف الراجحي — لو ده كشف بنك تاني، ` +
          `مش هينفع دلوقتي.`,
      )
    }

    return {
      rows: outcome.rows,
      errors: outcome.errors,
      pagesRead: outcome.pagesRead,
      content: canonicalContent(outcome.rows),
    }
  }
}
