import type { IdGenerator } from '../../application/ports/repositories'
import type { Id } from '../../domain/entities/types'

/**
 * مولّد معرّفات للتشغيل الحقيقي.
 *
 * ⚠️ لماذا لا يصلح SequentialIdGenerator هنا:
 * هو يبدأ العدّ من 1 في كل جلسة، فاستيراد ثانٍ بعد إعادة فتح التطبيق
 * ينتج `txn-000001` مرة أخرى ويكتب **فوق** عملية موجودة في Firestore.
 * التسلسلي للاختبار فقط، حيث الحتمية مطلوبة والحالة تبدأ فارغة.
 *
 * الصيغة: <بادئة>-<توقيت base36 تصاعدي>-<عشوائي>
 * التوقيت أولًا فيبقى الترتيب المعجمي مطابقًا للزمني، وهو ما يجعل
 * ترقيم صفحات Firestore بالمعرّف مفيدًا بلا فهرس إضافي.
 */
export class RandomIdGenerator implements IdGenerator {
  private lastMillis = 0
  private counter = 0

  next(prefix: string): Id {
    const now = Date.now()
    // ضمان التفرّد داخل نفس المللي ثانية
    if (now === this.lastMillis) this.counter++
    else {
      this.lastMillis = now
      this.counter = 0
    }

    const time = now.toString(36).padStart(9, '0')
    const seq = this.counter.toString(36).padStart(3, '0')
    return `${prefix}-${time}${seq}-${randomSuffix()}`
  }
}

function randomSuffix(): string {
  const bytes = new Uint8Array(6)
  // crypto متاح في كل المتصفحات المستهدفة وفي Node 19+
  crypto.getRandomValues(bytes)
  let out = ''
  for (const byte of bytes) out += byte.toString(36).padStart(2, '0')
  return out
}
