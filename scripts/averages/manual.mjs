/**
 * الأرقام **اليدوية** في `public/averages.json` — مالهاش مصدر بيتسحب آليًا، فبتتكتب هنا بمصدرها وتاريخها
 * وعليها «يدوي — اتراجع يوم <التاريخ>». أي تحديث ليها = تعديل هنا بإيد + تاريخ مراجعة جديد (OVERRIDES §69.6).
 *
 * `official`: الرقم نفسه صادر من جهة رسمية (بنك مركزي · هيئة إحصاء) ولا من جهة خاصة (مزوّد مؤشرات · موقع عقارات).
 * `confidence`: «C» = الرقم اتنقل من خبر صحفي مش من صفحة الجهة نفسها (موقع الجهة كان بيرفض السحب الآلي).
 */
import { geometricMeanBp } from './math.mjs'

export const MANUAL_REVIEWED_ON = '2026-10-05'

/** مؤشر عقارمب (خاص) لمصر — التغيّر السنوي بالمية لكل سنة (2020–2024)، والمتوسط الهندسي بيتحسب منهم مش بيتكتب بإيد. */
export const AQARMAP_YEARLY_PERCENTS = ['-9.6', '3.6', '25.4', '41.9', '18.2']

export function manualEntries() {
  const base = { kind: 'manual', reviewedOn: MANUAL_REVIEWED_ON, stale: false }
  return {
    STOCKS_SA: {
      ...base,
      valueBp: 795,
      currency: 'USD',
      periodStart: '2014-12',
      periodEnd: '2024-12',
      official: false,
      sourceName: 'MSCI Saudi Arabia Index (USD) — Net total return, 10Y annualized',
      sourceUrl: 'https://www.msci.com/downloads/web/msci-com/indexes/index-category/saudi-arabia-indexes/msci-saudi-arabia-index-usd-net.pdf',
      method: 'رقم المؤشر نفسه (10 سنين لآخر ديسمبر 2024، مع التوزيعات بعد الضريبة). بالدولار = بالريال (الربط 3.75).',
    },
    STOCKS_EG: {
      ...base,
      valueBp: 1942,
      currency: 'EGP',
      periodStart: '2015-12',
      periodEnd: '2025-12',
      official: false,
      confidence: 'C',
      sourceName: 'EGX30 price index year-end closes 7,089.06 → 41,828.97 (tables from 1stock1 and Afrivestia, not the EGX site)',
      sourceUrl: null,
      method: 'المعدل المركّب لإغلاق آخر السنة — السعر بس من غير التوزيعات، وبالجنيه (أغلب الزيادة نزول الجنيه).',
    },
    POLICY_RATE_SA: {
      ...base,
      valueBp: 400,
      currency: 'SAR',
      periodStart: '2026-09',
      periodEnd: '2026-09',
      official: true,
      confidence: 'C',
      sourceName: 'SAMA reverse repo rate after the 16–17 Sep 2026 decision (via Arab News, 2026-09-17)',
      sourceUrl: null,
      method: 'معلومة جنب فايدة بنكك — مش متوسط ومش بيتحط مكانها.',
    },
    POLICY_RATE_EG: {
      ...base,
      valueBp: 1900,
      currency: 'EGP',
      periodStart: '2026-09',
      periodEnd: '2026-09',
      official: true,
      confidence: 'C',
      sourceName: 'CBE overnight deposit rate, held on 24 Sep 2026 (via news reports)',
      sourceUrl: null,
      method: 'معلومة جنب فايدة بنكك — مش متوسط ومش بيتحط مكانها.',
    },
    REAL_ESTATE_INDEX_SA: {
      ...base,
      valueBp: 260,
      currency: 'SAR',
      periodStart: '2025-06',
      periodEnd: '2026-06',
      official: true,
      confidence: 'C',
      sourceName: 'GASTAT Real Estate Price Index — residential, Q2 2026 year on year (via Arab News, 2026-07-20)',
      sourceUrl: null,
      method: 'تغيّر سنة واحدة بس (المؤشر غيّر طريقته في الربع التالت 2024 فمفيش سلسلة طويلة بنفس الطريقة).',
    },
    REAL_ESTATE_INDEX_EG: {
      ...base,
      valueBp: geometricMeanBp(AQARMAP_YEARLY_PERCENTS),
      currency: 'EGP',
      periodStart: '2019',
      periodEnd: '2024',
      official: false,
      confidence: 'C',
      sourceName: `Aqarmap price index yearly changes 2020–2024: ${AQARMAP_YEARLY_PERCENTS.join(', ')}% (private; no official Egyptian house-price index found)`,
      sourceUrl: null,
      method: 'المتوسط الهندسي للتغيّر السنوي لخمس سنين — مؤشر موقع عقارات خاص، مش رسمي.',
    },
  }
}
