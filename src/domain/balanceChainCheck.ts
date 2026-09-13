import { addMoney, subtractMoney } from './money'
import type { StoredRow } from './idRepair'

/**
 * اتساق سلسلة رصيد الكشف — دليل قبل تنظيف البقايا (HANDOVER §36).
 *
 * كل سطر فيه رصيد معلن لازم يساوي رصيد السطر اللي قبله ± مبلغه. المقارنة **محلية**
 * (سطر بسطر) مش تراكمية: عملية زيادة واحدة بتعمل كسر واحد تقريبًا، وعملية حقيقية
 * اتشالت بتعمل كسر واحد — بدل ما فرق واحد يبوّظ كل اللي بعده.
 *
 * صفر كسر بعد التنظيف = مفيش مكرر فاضل **ومفيش** عملية حقيقية اتشالت.
 * بيرجّع أعداد وشهور ومعرّفات داخلية بس — مفيش مبالغ.
 *
 * الترتيب: التاريخ ثم `sourceOrder` (ترتيب السطر في الكشف — spec/02 لا يُعاد ترتيب اليوم)،
 * ومعرّف الوثيقة لكسر التعادل بشكل ثابت.
 */

export interface ChainScenario {
  /** سطور فيها رصيد معلن. */
  checked: number
  breaks: number
  /** «YYYY-MM» ← عدد الكسور. */
  breakMonths: Record<string, number>
}

interface Line {
  docId: string
  date: string
  order: number
  stated: number
  amount: number
  incoming: boolean
}

function toLines(rows: readonly StoredRow[]): Line[] {
  return rows
    .filter((row) => Number.isInteger(row.data.statedBalanceMinor)
      && Number.isInteger(row.data.amountMinor)
      && typeof row.data.occurredAt === 'string')
    .map((row) => ({
      docId: row.docId,
      date: row.data.occurredAt as string,
      order: Number(row.data.sourceOrder ?? 0),
      stated: row.data.statedBalanceMinor as number,
      amount: row.data.amountMinor as number,
      incoming: row.data.observedDirection === 'in',
    }))
    .sort((a, b) => a.date !== b.date ? (a.date < b.date ? -1 : 1)
      : a.order !== b.order ? a.order - b.order
      : a.docId < b.docId ? -1 : a.docId > b.docId ? 1 : 0)
}

/** السطر `line` مش مساوي لرصيد `previous` ± مبلغه. */
function isBreak(previous: Line | null, line: Line | null): 0 | 1 {
  if (!previous || !line) return 0
  const expected = line.incoming ? addMoney(previous.stated, line.amount) : subtractMoney(previous.stated, line.amount)
  return expected === line.stated ? 0 : 1
}

export function statementChainBreaks(rows: readonly StoredRow[], exclude: ReadonlySet<string>): ChainScenario {
  const lines = toLines(rows).filter((line) => !exclude.has(line.docId))
  let breaks = 0
  const breakMonths: Record<string, number> = {}
  for (let i = 1; i < lines.length; i++) {
    if (isBreak(lines[i - 1], lines[i])) {
      breaks++
      const month = lines[i].date.slice(0, 7)
      breakMonths[month] = (breakMonths[month] ?? 0) + 1
    }
  }
  return { checked: lines.length, breaks, breakMonths }
}

export interface CandidateVerdicts {
  /** وجودها في السلسلة بيكسرها — دليل رصيد إنها زيادة. */
  breaksChain: string[]
  /** غيابها هو اللي بيكسر السلسلة — دليل رصيد إنها حقيقية. */
  neededByChain: string[]
  /** مالهاش أثر، أو مالهاش رصيد معلن فالسلسلة ما تقدرش تحكم. */
  unclear: string[]
}

/**
 * حكم سلسلة الرصيد على كل مرشح **لوحده**، والباقي ثابت: السلسلة الأساسية = كل الصفوف
 * ناقص `removed`. المرشح بيتقارن وهو موجود بين جيرانه الموجودين، وهو غايب.
 * الحساب بجيرانه بس لأن المقارنة محلية — مش بيعيد السلسلة كلها لكل مرشح.
 */
export function judgeCandidates(rows: readonly StoredRow[], removed: ReadonlySet<string>, candidates: readonly string[]): CandidateVerdicts {
  const lines = toLines(rows)
  const index = new Map(lines.map((line, i) => [line.docId, i]))
  const neighbor = (from: number, step: 1 | -1): Line | null => {
    for (let j = from + step; j >= 0 && j < lines.length; j += step) {
      if (!removed.has(lines[j].docId)) return lines[j]
    }
    return null
  }
  const verdicts: CandidateVerdicts = { breaksChain: [], neededByChain: [], unclear: [] }
  for (const docId of candidates) {
    const i = index.get(docId)
    if (i === undefined) { verdicts.unclear.push(docId); continue }
    const previous = neighbor(i, -1)
    const next = neighbor(i, 1)
    const present = isBreak(previous, lines[i]) + isBreak(lines[i], next)
    const absent = isBreak(previous, next)
    if (present > absent) verdicts.breaksChain.push(docId)
    else if (present < absent) verdicts.neededByChain.push(docId)
    else verdicts.unclear.push(docId)
  }
  return verdicts
}
