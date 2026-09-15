import { describe, it, expect } from 'vitest'
import { splitForWrite } from '../../src/infrastructure/saveTextFile'

/** حفظ الملفات الكبيرة على أندرويد على دفعات — اتشاف وقوع التطبيق مع النسخة الشاملة (2026-09-15). */
describe('تقسيم المحتوى قبل كتابته في الملف المؤقت', () => {
  it('الدفعات لما تتجمع بترجع نفس النص بالظبط', () => {
    const text = 'مصروفي '.repeat(1000)
    const parts = splitForWrite(text, 97)
    expect(parts.join('')).toBe(text)
    expect(parts.every((p) => p.length <= 97)).toBe(true)
  })

  it('ما بيقطعش إيموجي (حرفين) في النص بين دفعتين', () => {
    const text = 'a'.repeat(9) + '😀' + 'b'.repeat(5)
    const parts = splitForWrite(text, 10)
    expect(parts.join('')).toBe(text)
    for (const part of parts) {
      const last = part.charCodeAt(part.length - 1)
      expect(last >= 0xd800 && last <= 0xdbff).toBe(false)
    }
  })

  it('نص فاضي ⇒ دفعة واحدة فاضية (الملف المؤقت بيتعمل برضه)', () => {
    expect(splitForWrite('')).toEqual([''])
  })
})
