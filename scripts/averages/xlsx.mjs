/**
 * قارئ xlsx صغير **من غير مكتبة** (`package.json` مفيهوش قارئ إكسل، والقاعدة 8: لا مكتبة بلا سبب) — `node:zlib` بس.
 * ملف xlsx = ملف zip جواه XML. بنقرا شيت واحد بالاسم كقيم نصية (رقم أو نص)، من غير تنسيق ولا معادلات.
 * أي شكل مش متوقع ⇒ بيرمي، والمهمة بتسيب الرقم القديم وتعلّمه «قديم» (مش صفر).
 */
import { inflateRawSync } from 'node:zlib'

const EOCD = 0x06054b50
const CENTRAL = 0x02014b50
const LOCAL = 0x04034b50

/** ملفات الـzip كلها: الاسم ⇒ المحتوى (Buffer). بيدعم التخزين من غير ضغط (0) والضغط العادي (8). */
export function readZip(buffer) {
  let eocd = buffer.length - 22
  while (eocd >= 0 && buffer.readUInt32LE(eocd) !== EOCD) eocd--
  if (eocd < 0) throw new Error('الملف مش zip')
  const count = buffer.readUInt16LE(eocd + 10)
  let p = buffer.readUInt32LE(eocd + 16)
  const files = new Map()
  for (let i = 0; i < count; i++) {
    if (buffer.readUInt32LE(p) !== CENTRAL) throw new Error('فهرس الـzip بايظ')
    const method = buffer.readUInt16LE(p + 10)
    const compressed = buffer.readUInt32LE(p + 20)
    const nameLength = buffer.readUInt16LE(p + 28)
    const extraLength = buffer.readUInt16LE(p + 30)
    const commentLength = buffer.readUInt16LE(p + 32)
    const local = buffer.readUInt32LE(p + 42)
    const name = buffer.toString('utf8', p + 46, p + 46 + nameLength)
    if (buffer.readUInt32LE(local) !== LOCAL) throw new Error(`«${name}» بايظ`)
    const start = local + 30 + buffer.readUInt16LE(local + 26) + buffer.readUInt16LE(local + 28)
    const data = buffer.subarray(start, start + compressed)
    if (method === 0) files.set(name, data)
    else if (method === 8) files.set(name, inflateRawSync(data))
    else throw new Error(`طريقة ضغط ${method} مش مدعومة`)
    p += 46 + nameLength + extraLength + commentLength
  }
  return files
}

const ENTITIES = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" }
const unescape = (s) => s.replace(/&(amp|lt|gt|quot|apos);/g, (_, e) => ENTITIES[e])

/** رقم العمود من حروفه: A=0 · Z=25 · AA=26 … */
export function columnIndex(letters) {
  let n = 0
  for (const ch of letters) n = n * 26 + ch.charCodeAt(0) - 64
  return n - 1
}

/** صفوف الشيت [sheetName]: كل صف = مصفوفة نصوص (الخانة الفاضية `undefined`). */
export function readSheet(buffer, sheetName) {
  const files = readZip(buffer)
  const text = (name) => {
    const f = files.get(name)
    if (!f) throw new Error(`«${name}» مش موجود في الملف`)
    return f.toString('utf8')
  }
  const workbook = text('xl/workbook.xml')
  const sheetTag = [...workbook.matchAll(/<sheet\b[^>]*>/g)].map((m) => m[0]).find((tag) => attr(tag, 'name') === sheetName)
  if (!sheetTag) throw new Error(`الشيت «${sheetName}» مش موجود`)
  const relId = attr(sheetTag, 'r:id')
  const rels = text('xl/_rels/workbook.xml.rels')
  const relTag = [...rels.matchAll(/<Relationship\b[^>]*>/g)].map((m) => m[0]).find((tag) => attr(tag, 'Id') === relId)
  if (!relTag) throw new Error('مسار الشيت مش موجود')
  const target = attr(relTag, 'Target').replace(/^\/?(xl\/)?/, '')
  const shared = files.has('xl/sharedStrings.xml')
    ? [...text('xl/sharedStrings.xml').matchAll(/<si>([\s\S]*?)<\/si>/g)].map((m) =>
        unescape([...m[1].matchAll(/<t\b[^>]*>([\s\S]*?)<\/t>/g)].map((t) => t[1]).join('')),
      )
    : []
  const rows = []
  for (const row of text(`xl/${target}`).matchAll(/<row\b[^>]*>([\s\S]*?)<\/row>/g)) {
    const out = []
    for (const c of row[1].matchAll(/<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
      const ref = attr(c[1], 'r')
      if (!ref) continue
      const type = attr(c[1], 't')
      const body = c[2] ?? ''
      let value
      if (type === 'inlineStr') value = unescape((body.match(/<t\b[^>]*>([\s\S]*?)<\/t>/) ?? [])[1] ?? '')
      else {
        const v = (body.match(/<v>([\s\S]*?)<\/v>/) ?? [])[1]
        if (v === undefined) continue
        value = type === 's' ? shared[Number(v)] : unescape(v)
      }
      out[columnIndex(ref.match(/^[A-Z]+/)[0])] = value
    }
    rows.push(out)
  }
  return rows
}

function attr(tag, name) {
  const m = tag.match(new RegExp(`\\s${name.replace(':', '\\:')}="([^"]*)"`))
  return m ? m[1] : undefined
}
