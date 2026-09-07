/**
 * تصحيح النص العربي الخارج من PDF — دوال نقية بلا أي معرفة بـpdfjs.
 *
 * ⚠️ فرق حقيقي عن السكربت القديم (`legacy/parse_alrajhi.py`):
 * هو **يعكس كل كلمة عربية** لأن `pdfplumber` يسلّمها معكوسة بصيغة العرض.
 * `pdfjs` **لا يفعل ذلك** — التطبيع (NFKC) وحده يكفي، والعكس يفسد النص:
 * «الرصيد» تصبح «ديصرلا».
 *
 * نقل السلوك القديم حرفيًا كان سينتج أسماء تجار مقلوبة **بلا خطأ ظاهر**،
 * ولذلك هذا الملف مختبَر بنص عربي حقيقي من الكشف.
 */

/** أقواس الاتجاه تنعكس مرآتيًا في ترتيب العرض. */
const MIRRORED: Record<string, string> = {
  '(': ')',
  ')': '(',
  '[': ']',
  ']': '[',
  '{': '}',
  '}': '{',
}

const ARABIC = /[؀-ۿﭐ-﷿ﹰ-﻿]/

export function hasArabic(text: string): boolean {
  return ARABIC.test(text)
}

/**
 * يحوّل صيغ العرض (presentation forms) لحروف عربية عادية.
 *
 * `ﺍﻟﺮﺻﻴﺪ` ⇒ `الرصيد`. بدون ده النص بيبان مقروءًا في الطرفية
 * لكنه يفشل في أي بحث أو مقارنة، لأن حروفه ترميزها مختلف.
 */
export function normalizeArabic(text: string): string {
  return text.normalize('NFKC')
}

/** يعكس الأقواس المرآتية في نص عربي. */
function unmirror(text: string): string {
  return [...text].map((ch) => MIRRORED[ch] ?? ch).join('')
}

export interface PositionedWord {
  /** الحافة اليسرى بالنقاط. */
  x: number
  /** الارتفاع من أسفل الصفحة بالنقاط. */
  y: number
  text: string
}

/**
 * يجمع كلمات سطر في نص واحد بالترتيب الصحيح.
 *
 * السطر الذي فيه عربي يُقرأ **من اليمين لليسار**، فالكلمة ذات `x` الأكبر
 * تأتي أولًا. السطر اللاتيني الخالص يبقى من اليسار لليمين.
 */
export function joinLine(words: readonly PositionedWord[]): string {
  if (words.length === 0) return ''

  const normalized = words.map((w) => ({ ...w, text: normalizeArabic(w.text) }))
  const rtl = normalized.some((w) => hasArabic(w.text))

  const ordered = [...normalized].sort((a, b) => (rtl ? b.x - a.x : a.x - b.x))

  return ordered
    .map((w) => (hasArabic(w.text) ? unmirror(w.text) : w.text))
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim()
}

/**
 * ينظّف نصًا للعرض: يشيل المسافات الزائدة ويقصّه عند حد **معلن**.
 * القص للعرض فقط — النص الكامل بيتحفظ في `description` بلا قص (spec/04).
 */
export function tidy(text: string, maxLength = 160): string {
  const clean = normalizeArabic(text).replace(/\s+/g, ' ').trim()
  return clean.length > maxLength ? `${clean.slice(0, maxLength - 1)}…` : clean
}
