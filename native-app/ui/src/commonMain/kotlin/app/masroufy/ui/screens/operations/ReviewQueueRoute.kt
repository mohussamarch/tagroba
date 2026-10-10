package app.masroufy.ui.screens.operations

import app.masroufy.ui.nav.Route

/**
 * «المراجعة» (`ReviewQueue` — منطقة «العمليات»): حاسبة الادخار بتفتحها من «حدّد نوع هذه العمليات» لما شهر من الثلاثة فيه عمليات نوعها مش معروف.
 *
 * ⚠️ **مكان مؤقت** لحد الدمج: فرع شاشات «العمليات» (`screens-operations`) معرّف نفس المسار بنفس الاسم والحزمة في `OperationsRoutes.kt`
 * (وهو اللي بيسجّل الشاشة). وقت الدمج الكومبايلر هيقول «Redeclaration» ⇒ **امسح الملف ده** — حاسبة الادخار بتستورده بالاسم ده فهتشتغل على طول.
 * لحد كده: مش متسجّل ⇒ «قيد البناء» (مش وقوع).
 */
object ReviewQueueRoute : Route {
    override val name = "ReviewQueue"
}
