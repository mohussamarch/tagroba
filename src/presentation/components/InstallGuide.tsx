import { Smartphone } from 'lucide-react'

export function InstallGuide() {
 return <section className="card" aria-label="تثبيت التطبيق">
  <h2 className="card__title"><Smartphone size={16} aria-hidden="true" />مصروفي على شاشة الموبايل</h2>
  <p>على سامسونج: افتح رابط مصروفي في Chrome، ومن قائمة ⋮ اختار «إضافة إلى الشاشة الرئيسية» أو «تثبيت التطبيق» لو ظهرت.</p>
  <p>بعد التثبيت افتحه من أيقونته، واقفله وافتحه تاني. اتأكد إن تسجيل الدخول وبياناتك موجودين.</p>
  <p className="settings__hint">لو فاتح نسخة محلية للتجربة، استخدم رابط التطبيق المنشور على الموبايل.</p>
 </section>
}
