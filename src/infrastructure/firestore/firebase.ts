import { initializeApp, type FirebaseApp } from 'firebase/app'
import {
  getAuth,
  GoogleAuthProvider,
  browserLocalPersistence,
  setPersistence,
  type Auth,
} from 'firebase/auth'
import {
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  type Firestore,
} from 'firebase/firestore'

/**
 * تهيئة فايربيز — نقطة الاتصال الوحيدة بالخدمة.
 *
 * الإعدادات علنية بطبيعتها (FIREBASE.md)؛ ما يحمي البيانات هو قواعد
 * firestore.rules المنشورة، لا إخفاء المفاتيح.
 */

function required(name: string): string {
  const value = import.meta.env[name as keyof ImportMetaEnv] as string | undefined
  if (!value) {
    throw new Error(`إعداد فايربيز الناقص: ${name} — راجع ملف .env.local`)
  }
  return value
}

const firebaseConfig = {
  apiKey: required('VITE_FIREBASE_API_KEY'),
  authDomain: required('VITE_FIREBASE_AUTH_DOMAIN'),
  projectId: required('VITE_FIREBASE_PROJECT_ID'),
  storageBucket: required('VITE_FIREBASE_STORAGE_BUCKET'),
  messagingSenderId: required('VITE_FIREBASE_MESSAGING_SENDER_ID'),
  appId: required('VITE_FIREBASE_APP_ID'),
}

export const app: FirebaseApp = initializeApp(firebaseConfig)

/**
 * العمل بدون إنترنت (offline persistence) — إلزامي من اليوم الأول.
 * ARCHITECTURE.md §5.6: التطبيق يقرأ من نسخة الجهاز ويجلب المتغيّر فقط،
 * وده الحاجز الأساسي ضد تجاوز حد الـ 50,000 قراءة اليومي على باقة Spark.
 *
 * persistentMultipleTabManager يسمح بفتح التطبيق في أكثر من تبويب
 * بدون ما يفشل التخزين المحلي.
 */
export const db: Firestore = initializeFirestore(app, {
  localCache: persistentLocalCache({
    tabManager: persistentMultipleTabManager(),
  }),
})

export const auth: Auth = getAuth(app)
auth.languageCode = 'ar'

/** بقاء الجلسة بعد إغلاق المتصفح — التطبيق مثبت على الجوال كـ PWA. */
export const authPersistenceReady = setPersistence(auth, browserLocalPersistence)

export const googleProvider = new GoogleAuthProvider()
googleProvider.setCustomParameters({ prompt: 'select_account' })
