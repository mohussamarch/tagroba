/**
 * فحص نوع الملف قبل محاولة تحليله.
 *
 * ⚠️ **لماذا وُجد هذا الملف:** `File.text()` ينجح على **أي** ملف، ويرجّع
 * بايتات PDF كأنها نص. فكان المستخدم يختار كشف PDF فيصله خطأ من قارئ CSV:
 * «اقتباس مفتوح ولم يُغلق — الملف ناقص أو تالف عند السطر 3366».
 *
 * الرسالة دي **كذب**: الملف سليم تمامًا، والمشكلة أن التطبيق لا يقرأ PDF.
 * رسالة خطأ مضلِّلة أسوأ من غيابها — CLAUDE.md #14.
 *
 * الفحص هنا يسبق أي تحليل، ويقول الحقيقة بالضبط.
 */

export type FileKind = 'csv' | 'pdf' | 'binary' | 'empty'

export interface FileCheck {
  kind: FileKind
  /** هل يصلح للمتابعة إلى قارئ CSV؟ */
  ok: boolean
  /** ما الخطأ بلغة المستخدم، وماذا يفعل. null عند الصلاحية. */
  message: string | null
}

/** توقيع PDF في أول بايتات الملف. */
const PDF_SIGNATURE = '%PDF-'

/** نسبة محارف التحكم التي تعني «ملف ثنائي لا نصي». */
const BINARY_RATIO = 0.02
const SAMPLE_LENGTH = 4000

/** محرف NUL — لا يظهر في ملف نصي أبدًا، فوجوده وحده يكفي للحكم. */
const NUL = String.fromCharCode(0)

function controlCharRatio(sample: string): number {
  if (sample.length === 0) return 0
  let control = 0
  for (const ch of sample) {
    const code = ch.charCodeAt(0)
    // مسموح: جدولة (9)، سطر جديد (10)، إرجاع (13)
    if (code === 9 || code === 10 || code === 13) continue
    // 0xFFFD هو محرف الاستبدال الذي ينتج عن قراءة بايتات ثنائية كنص
    if (code < 32 || code === 127 || code === 0xfffd) control++
  }
  return control / sample.length
}

export function inspectFile(fileName: string, content: string): FileCheck {
  const trimmedStart = content.replace(/^﻿/, '')

  if (trimmedStart.trim() === '') {
    return { kind: 'empty', ok: false, message: 'الملف فاضي.' }
  }

  const lowerName = fileName.toLowerCase()

  /*
   * الـPDF بقى مقروءًا (ARCHITECTURE §19)، لكن **مش عبر قارئ CSV**:
   * `ok: false` هنا معناها «متكملش لقارئ النصوص»، والمتصل بيوجّهه
   * لقارئ الـPDF على أساس `kind`. الرسالة إرشاد مش خطأ.
   */
  if (trimmedStart.startsWith(PDF_SIGNATURE) || lowerName.endsWith('.pdf')) {
    return {
      kind: 'pdf',
      ok: false,
      message: 'ده ملف PDF — بنقراه بقارئ مختلف عن الـCSV.',
    }
  }

  const sample = trimmedStart.slice(0, SAMPLE_LENGTH)
  if (sample.indexOf(NUL) !== -1 || controlCharRatio(sample) > BINARY_RATIO) {
    const dot = lowerName.lastIndexOf('.')
    const ext = dot > 0 ? lowerName.slice(dot) : ''
    return {
      kind: 'binary',
      ok: false,
      message:
        `الملف ده مش ملف نصي${ext ? ` (${ext})` : ''}. ` +
        'المتوقع ملف CSV — يعني نص فيه أعمدة مفصولة بفواصل.',
    }
  }

  return { kind: 'csv', ok: true, message: null }
}
