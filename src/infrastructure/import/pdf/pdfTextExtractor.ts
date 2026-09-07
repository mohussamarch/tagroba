import type { PdfPage } from './alrajhiPdfSchema'
import type { PositionedWord } from './arabicText'

/**
 * الغلاف الوحيد حوالين `pdfjs-dist` — **المكان الوحيد اللي بيعرفها**.
 *
 * الملف ده رفيع عن قصد: كل التحليل في `alrajhiPdfSchema.ts` النقي،
 * فبيتختبر بلا مكتبة وبلا ملف PDF (`ARCHITECTURE §19.3`).
 *
 * المكتبة بتتحمّل **عند أول استيراد PDF بس** (`import()` ديناميكي)
 * فما تدخلش الحزمة الأولى — حجمها ~٣٠٠ ك.ب مضغوطة، وتكلفة تحميلها
 * على كل فتحة للتطبيق مش مبرَّرة لميزة بتتستخدم من وقت للتاني.
 */

export class PdfReadError extends Error {}

/** أقصى عدد صفحات مقبول — حد معلن يمنع تعليق المتصفح على ملف ضخم. */
export const MAX_PAGES = 400

export interface ExtractProgress {
  page: number
  total: number
}

/**
 * يستخرج كلمات كل صفحة بإحداثياتها.
 *
 * `x` من الحافة اليسرى و`y` من أسفل الصفحة — نفس نظام إحداثيات PDF،
 * والمخطط متقاس عليه مباشرة.
 */
export async function extractPdfPages(
  data: ArrayBuffer,
  onProgress?: (progress: ExtractProgress) => void,
): Promise<PdfPage[]> {
  const pdfjs = await loadPdfjs()

  let document
  try {
    document = await pdfjs.getDocument({ data: new Uint8Array(data) }).promise
  } catch (cause) {
    // ملف تالف أو محمي بكلمة سر — السبب بيتقال، ما بيتخمنش
    throw new PdfReadError(
      `مقدرناش نفتح ملف الـPDF. لو محمي بكلمة سر، شيل الحماية وجرّب تاني. ` +
        `(${cause instanceof Error ? cause.message : String(cause)})`,
    )
  }

  if (document.numPages > MAX_PAGES) {
    throw new PdfReadError(
      `الملف ${document.numPages} صفحة، والحد ${MAX_PAGES}. ` +
        `قسّم الكشف لفترات أقصر وجرّب تاني.`,
    )
  }

  const pages: PdfPage[] = []
  for (let pageNumber = 1; pageNumber <= document.numPages; pageNumber += 1) {
    const page = await document.getPage(pageNumber)
    const content = await page.getTextContent()

    const words: PositionedWord[] = []
    for (const item of content.items) {
      // العناصر ممكن تكون علامات بنية بلا نص — بتتخطى
      if (!('str' in item) || !item.str.trim()) continue
      words.push({ x: item.transform[4], y: item.transform[5], text: item.str })
    }
    pages.push({ pageNumber, words })

    // تحرير ذاكرة الصفحة فورًا — الكشف ممكن يكون مئات الصفحات
    page.cleanup()
    onProgress?.({ page: pageNumber, total: document.numPages })
  }

  await document.destroy()
  return pages
}

type Pdfjs = typeof import('pdfjs-dist')

let cached: Promise<Pdfjs> | null = null

/** يحمّل المكتبة مرة واحدة ويشغّل عاملها (worker). */
function loadPdfjs(): Promise<Pdfjs> {
  cached ??= (async () => {
    const pdfjs = await import('pdfjs-dist')
    // العامل بيشتغل في خيط منفصل فالواجهة ما بتتجمدش أثناء القراءة
    const worker = await import('pdfjs-dist/build/pdf.worker.min.mjs?url')
    pdfjs.GlobalWorkerOptions.workerSrc = worker.default
    return pdfjs
  })()
  return cached
}
