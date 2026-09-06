import { FirebaseAuthAdapter } from '../infrastructure/firestore/FirebaseAuthAdapter'
import type { AuthPort } from '../application/ports/AuthPort'

/**
 * نقطة التجميع الوحيدة (ARCHITECTURE.md §3).
 *
 * هنا فقط تُربط الواجهات (ports) بتنفيذها. لا مكتبة حقن اعتماديات.
 * في الاختبار تُمرَّر تنفيذات infrastructure/memory/ بدل firestore/،
 * وهذا ما يجعل اختبارات القبول تعمل بدون فايربيز إطلاقًا.
 *
 * كل ما يُصدَّر من هنا مكتوب بنوع الواجهة لا بنوع التنفيذ،
 * عشان لو اتغيّر التخزين لا يتغيّر أي مستدعٍ.
 */

export interface Container {
  auth: AuthPort
}

export function createContainer(): Container {
  return {
    auth: new FirebaseAuthAdapter(),
  }
}
