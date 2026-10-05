// كلمات كشف الـPDF بإحداثياتها ⇒ JSON — عشان قارئ كوتلن (`parseAlrajhiPdf`) يتختبر على الكشف الحقيقي.
// نفس القراية اللي التطبيق الحالي بيعملها (pdfjs + `getTextContent`، زي `tests/acceptance/alrajhiPdfReal.test.ts`)،
// فالفرق الوحيد بين التطبيقين يبقى في القارئ نفسه.
//
// ⚠️ **الناتج فيه بيانات المالك الحقيقية** ⇒ بيتكتب في `files/` (مستبعد من Git) ومش بيترفع أبدًا.
// التشغيل: node scripts/real/exportPdfPages.mjs <مسار الـPDF> <مسار الناتج>
import { readFileSync, writeFileSync } from 'node:fs'
import * as pdfjs from 'pdfjs-dist/legacy/build/pdf.mjs'

const [input, output] = process.argv.slice(2)
if (!input || !output) throw new Error('الاستعمال: node exportPdfPages.mjs <pdf> <json>')
if (!/[\\/]files[\\/]/.test(output)) throw new Error('الناتج لازم يكون جوه files/ (مستبعد من Git) — فيه بيانات حقيقية')

const doc = await pdfjs.getDocument({ data: new Uint8Array(readFileSync(input)), useSystemFonts: true }).promise
const pages = []
for (let n = 1; n <= doc.numPages; n += 1) {
  const content = await (await doc.getPage(n)).getTextContent()
  const words = []
  for (const item of content.items) {
    if (!('str' in item) || !item.str.trim()) continue
    words.push({ x: item.transform[4], y: item.transform[5], text: item.str })
  }
  pages.push({ pageNumber: n, words })
}
writeFileSync(output, JSON.stringify(pages))
console.log(`صفحات: ${pages.length} · كلمات: ${pages.reduce((s, p) => s + p.words.length, 0)}`)
