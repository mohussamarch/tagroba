/**
 * اختبارات المتوسطات (OVERRIDES §69.6) بردود ثابتة — من غير نت. التشغيل: `node --test scripts/averages/`
 * الأرقام المتوقعة = البحث المتراجع (10 سنين لحد ديسمبر 2025، أساس متوسط الشهر): ذهب بالدولار 14.88% · بالجنيه 34.2% ·
 * تضخم السعودية 1.74% · مصر 16.33%. أرقام الذهب والصرف والتضخم في الثوابت تحت **أرقام عامة من البنك الدولي** (مش بيانات حد).
 */
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { deflateRawSync } from 'node:zlib'
import { buildAverages, isDue } from '../fetch-averages.mjs'
import { cagrBp, devaluationPartBp, geometricMeanBp, ppmToBp, rootRatePpm, toScaled } from './math.mjs'
import { CMO_FALLBACK_URL, findCmoLink, wdiUrl } from './sources.mjs'
import { readSheet } from './xlsx.mjs'

const GOLD = {
  2015: [1251, 1227, 1179, 1199, 1199, 1182, 1128, 1118, 1125, 1159, 1086, 1076],
  2016: [1098, 1200, 1245, 1242, 1261, 1276, 1337, 1340, 1327, 1267, 1238, 1157],
  2025: [2710, 2895, 2983, 3218, 3309, 3353, 3340, 3368, 3668, 4058, 4087, 4309],
}
const FX_EGY = { 2014: 7.07760856060606, 2015: 7.69125833333333, 2016: 10.0254007885465, 2025: 49.2277558333333, 2026: null }
const CPI_SAU = { 2015: 1.22269318552328, 2016: 2.05347076823965, 2017: -0.834845735027162, 2018: 2.46594308994843, 2019: -1.19298644533328, 2020: 3.37234961012303, 2021: 3.06328988941548, 2022: 2.47407371925372, 2023: 2.32708518362706, 2024: 1.68792112375809, 2025: 2.08420853585036 }
const CPI_EGY = { 2015: 10.370490343517, 2016: 13.813606214829, 2017: 29.5066083940039, 2018: 14.4014657807422, 2019: 9.15279959324804, 2020: 5.04493288977539, 2021: 5.21404940513043, 2022: 13.8956609775178, 2023: 33.8847763115546, 2024: 28.2705899322083, 2025: 14.0735136645934 }

/* ───── xlsx صغير للاختبار (zip من غير ضغط + ملف واحد مضغوط عشان الطريقتين يتجربوا) ───── */
function zip(entries) {
  const locals = []
  const centrals = []
  let offset = 0
  for (const [name, text, compress] of entries) {
    const raw = Buffer.from(text, 'utf8')
    const data = compress ? deflateRawSync(raw) : raw
    const nameBuf = Buffer.from(name, 'utf8')
    const local = Buffer.alloc(30)
    local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(compress ? 8 : 0, 8)
    local.writeUInt32LE(data.length, 18); local.writeUInt32LE(raw.length, 22); local.writeUInt16LE(nameBuf.length, 26)
    const central = Buffer.alloc(46)
    central.writeUInt32LE(0x02014b50, 0); central.writeUInt16LE(compress ? 8 : 0, 10)
    central.writeUInt32LE(data.length, 20); central.writeUInt32LE(raw.length, 24); central.writeUInt16LE(nameBuf.length, 28); central.writeUInt32LE(offset, 42)
    locals.push(local, nameBuf, data)
    centrals.push(central, nameBuf)
    offset += 30 + nameBuf.length + data.length
  }
  const cd = Buffer.concat(centrals)
  const end = Buffer.alloc(22)
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(entries.length, 8); end.writeUInt16LE(entries.length, 10)
  end.writeUInt32LE(cd.length, 12); end.writeUInt32LE(offset, 16)
  return Buffer.concat([...locals, cd, end])
}

function cmoWorkbook({ withGold = true } = {}) {
  const strings = ['World Bank Commodity Price Data (The Pink Sheet)', 'Crude oil, average', withGold ? 'Gold' : 'Silver']
  const months = []
  for (const [year, values] of Object.entries(GOLD)) values.forEach((v, i) => months.push([`${year}M${String(i + 1).padStart(2, '0')}`, v]))
  const rows = [
    `<row r="1"><c r="A1" t="s"><v>0</v></c></row>`,
    `<row r="5"><c r="B5" t="s"><v>1</v></c><c r="BR5" t="s"><v>2</v></c></row>`,
    ...months.map(([label, v], i) => `<row r="${7 + i}"><c r="A${7 + i}" t="inlineStr"><is><t>${label}</t></is></c><c r="B${7 + i}"><v>78.900000000000006</v></c><c r="BR${7 + i}"><v>${v}</v></c></row>`),
  ]
  return zip([
    ['xl/workbook.xml', '<workbook><sheets><sheet name="Mismatch Details" sheetId="1" r:id="rId1"/><sheet name="Monthly Prices" sheetId="2" r:id="rId2"/></sheets></workbook>'],
    ['xl/_rels/workbook.xml.rels', '<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Target="worksheets/sheet2.xml"/></Relationships>'],
    ['xl/sharedStrings.xml', `<sst>${strings.map((s) => `<si><t>${s}</t></si>`).join('')}</sst>`],
    ['xl/worksheets/sheet1.xml', '<worksheet><sheetData/></worksheet>'],
    ['xl/worksheets/sheet2.xml', `<worksheet><sheetData>${rows.join('')}</sheetData></worksheet>`, true],
  ])
}

const wdi = (values) => [{ page: 1 }, Object.entries(values).map(([date, value]) => ({ date, value })).reverse()]
const PAGE_LINK = 'https://thedocs.worldbank.org/en/doc/abc-0050012026/related/CMO-Historical-Data-Monthly.xlsx'

function stubHttp({ failing = [], page = `<a href="${PAGE_LINK}">Monthly prices</a>`, workbook = cmoWorkbook() } = {}) {
  const calls = []
  const replies = {
    'commodity-markets': page,
    [PAGE_LINK]: workbook,
    [CMO_FALLBACK_URL]: workbook,
    [wdiUrl('EGY', 'PA.NUS.FCRF')]: wdi(FX_EGY),
    [wdiUrl('SAU', 'FP.CPI.TOTL.ZG')]: wdi(CPI_SAU),
    [wdiUrl('EGY', 'FP.CPI.TOTL.ZG')]: wdi(CPI_EGY),
  }
  const reply = (url) => {
    calls.push(url)
    if (failing.some((f) => url.includes(f))) throw new Error(`UNABLE_TO_GET_ISSUER_CERT (${url})`)
    const key = Object.keys(replies).find((k) => url.includes(k))
    if (key === undefined) throw new Error(`مش متوقع: ${url}`)
    return replies[key]
  }
  return { calls, text: async (u) => reply(u), json: async (u) => reply(u), buffer: async (u) => reply(u) }
}

const NOW = new Date('2026-10-01T02:00:00Z')

test('أرقام البحث بالظبط: ذهب 14.88% · بالجنيه 34.2% · تضخم 1.74% و16.33%', async () => {
  const feed = await buildAverages({ http: stubHttp(), previous: null, now: NOW })
  const a = feed.averages
  assert.equal(a.GOLD_USD.valueBp, 1488)
  assert.deepEqual([a.GOLD_USD.periodStart, a.GOLD_USD.periodEnd], ['2015-12', '2025-12'])
  assert.equal(a.GOLD_EGP.valueBp, 3422)
  assert.deepEqual([a.GOLD_EGP.periodStart, a.GOLD_EGP.periodEnd], ['2015', '2025'])
  assert.equal(a.CPI_SA.valueBp, 174)
  assert.equal(a.CPI_EG.valueBp, 1633)
  assert.deepEqual([a.CPI_EG.periodStart, a.CPI_EG.periodEnd], ['2015', '2025'])
  // نزول الجنيه = (1 + بالجنيه) ÷ (1 + بالدولار) − 1 على نفس الأساس ≈ متوسط صرف الجنيه نفسه
  assert.equal(a.GOLD_USD_ANNUAL.valueBp, 1148)
  assert.equal(a.EGP_PER_USD.valueBp, 2040)
  assert.ok(Math.abs(devaluationPartBp(a.GOLD_EGP.valueBp, a.GOLD_USD_ANNUAL.valueBp) - a.EGP_PER_USD.valueBp) <= 1)
  assert.equal(a.GOLD_USD.sourceUrl, PAGE_LINK, 'الرابط من الصفحة مش الاحتياطي')
  assert.equal(feed.failures.length, 0)
  assert.equal(a.GOLD_USD.computedOn, '2026-10-01')
})

test('كل سطر عليه رقم صحيح ومصدر وفترة ورسمي ولا خاص — واليدوي عليه تاريخ المراجعة', async () => {
  const feed = await buildAverages({ http: stubHttp(), previous: null, now: NOW })
  for (const [key, e] of Object.entries(feed.averages)) {
    assert.ok(Number.isInteger(e.valueBp), key)
    assert.ok(e.sourceName && e.method && e.periodStart && e.periodEnd, key)
    assert.equal(typeof e.official, 'boolean', key)
    assert.ok(['computed', 'manual'].includes(e.kind), key)
    if (e.kind === 'manual') assert.equal(e.reviewedOn, '2026-10-05', key)
  }
  const a = feed.averages
  assert.equal(a.STOCKS_SA.valueBp, 795)
  assert.equal(a.STOCKS_SA.official, false)
  assert.equal(a.POLICY_RATE_SA.valueBp, 400)
  assert.equal(a.POLICY_RATE_EG.valueBp, 1900)
  assert.equal(a.REAL_ESTATE_INDEX_SA.valueBp, 260)
  assert.equal(a.REAL_ESTATE_INDEX_EG.valueBp, 1452, 'عقارمب 2020–2024 هندسي ≈ 14.5%')
  assert.equal(a.REAL_ESTATE_INDEX_EG.official, false)
})

test('مصدر فشل ⇒ الرقم القديم بيفضل وعليه «قديم» — ومفيش قديم ⇒ السطر غايب، مش صفر', async () => {
  const first = await buildAverages({ http: stubHttp(), previous: null, now: NOW })
  const later = new Date('2026-11-01T02:00:00Z')
  const second = await buildAverages({ http: stubHttp({ failing: ['FP.CPI.TOTL.ZG'] }), previous: first, now: later })
  assert.equal(second.averages.CPI_SA.valueBp, 174)
  assert.equal(second.averages.CPI_SA.stale, true)
  assert.equal(second.averages.CPI_SA.staleSince, '2026-11-01')
  assert.match(second.averages.CPI_SA.staleReason, /UNABLE_TO_GET_ISSUER_CERT/)
  assert.equal(second.averages.GOLD_USD.stale, false, 'اللي نجح طازة')
  assert.equal(second.failures.length, 2)
  // فشل تاني الشهر اللي بعده ⇒ «قديم من» بيفضل أول يوم
  const third = await buildAverages({ http: stubHttp({ failing: ['FP.CPI.TOTL.ZG'] }), previous: second, now: new Date('2026-12-01T02:00:00Z') })
  assert.equal(third.averages.CPI_EG.staleSince, '2026-11-01')
  // أول مرة والمصدر واقع ⇒ مفيش سطر خالص
  const none = await buildAverages({ http: stubHttp({ failing: ['thedocs', 'commodity-markets'] }), previous: null, now: NOW })
  for (const key of ['GOLD_USD', 'GOLD_EGP', 'GOLD_USD_ANNUAL', 'EGP_PER_USD']) assert.equal(none.averages[key], undefined, key)
  assert.ok(Object.values(none.averages).every((e) => e.valueBp !== 0), 'ولا سطر صفر')
})

test('صفحة البنك الدولي واقعة ⇒ آخر رابط معروف · والملف من غير عمود Gold ⇒ فشل بالسبب', async () => {
  const http = stubHttp({ failing: ['commodity-markets'] })
  const feed = await buildAverages({ http, previous: null, now: NOW })
  assert.equal(feed.averages.GOLD_USD.sourceUrl, CMO_FALLBACK_URL)
  assert.ok(http.calls.includes(CMO_FALLBACK_URL))
  assert.equal(findCmoLink('<html>no link</html>'), null)
  const bad = await buildAverages({ http: stubHttp({ workbook: cmoWorkbook({ withGold: false }) }), previous: null, now: NOW })
  assert.equal(bad.averages.GOLD_USD, undefined)
  assert.match(bad.failures[0].reason, /Gold/)
})

test('الجدولة: يوم 1 · أو أقدم من 28 يوم · أو مفيش ملف · أو إجباري', () => {
  const prev = { generatedAt: '2026-10-01T02:00:00Z' }
  assert.equal(isDue(null, new Date('2026-10-15T02:00:00Z')), true)
  assert.equal(isDue(prev, new Date('2026-10-15T02:00:00Z')), false)
  assert.equal(isDue(prev, new Date('2026-10-29T01:00:00Z')), false, '27.96 يوم')
  assert.equal(isDue(prev, new Date('2026-10-29T02:00:00Z')), true, '28 يوم بالظبط')
  assert.equal(isDue({ generatedAt: '2026-10-20T02:00:00Z' }, new Date('2026-11-01T02:00:00Z')), true, 'يوم 1')
  assert.equal(isDue(prev, new Date('2026-10-15T02:00:00Z'), true), true)
  assert.equal(isDue({ generatedAt: 'بايظ' }, new Date('2026-10-15T02:00:00Z')), true)
})

test('الحساب الصحيح: معدلات معروفة على الورق والتقريب', () => {
  assert.equal(cagrBp(100n, 200n, 1), 10_000) // ضعف في سنة = 100%
  assert.equal(cagrBp(100n, 121n, 2), 1_000) // 1.1² = 1.21
  assert.equal(cagrBp(121n, 100n, 2), -909) // 1/1.1 − 1 = −9.0909%
  assert.equal(cagrBp(5n, 5n, 10), 0)
  assert.equal(rootRatePpm(121n, 100n, 2), 100_000n)
  assert.equal(ppmToBp(149n), 1)
  assert.equal(ppmToBp(150n), 2, 'النص لفوق')
  assert.equal(ppmToBp(-150n), -1, 'النص لفوق في السالب')
  assert.equal(ppmToBp(-151n), -2)
  assert.equal(toScaled('78.900000000000006', 6), 78_900_000n, 'من النص — من غير خطأ العشري')
  assert.equal(toScaled(0.29, 2), 29n)
  assert.equal(geometricMeanBp(['10', '10']), 1_000)
  assert.equal(geometricMeanBp(['-50', '100']), 0, '0.5 × 2 = 1')
  assert.equal(devaluationPartBp(3422, 1148), 2040)
})

test('قارئ الإكسل: شيت بالاسم والخانات بمكانها (ضغط وتخزين)', () => {
  const rows = readSheet(cmoWorkbook(), 'Monthly Prices')
  assert.equal(rows[1][69], 'Gold') // BR = العمود 70
  assert.equal(rows[2][0], '2015M01')
  assert.equal(rows[2][69], '1251')
  assert.throws(() => readSheet(cmoWorkbook(), 'Nope'), /Nope/)
})
