package app.masroufy.wiring

import app.masroufy.core.Space
import app.masroufy.usecase.LoadOnlineFeeds

/**
 * كل اللي أي منطقة ممكن تحتاجه عشان تبني حالات استخدامها — **نفس الشكل لكل `<Area>Graph(c: AreaContext)`**، فـ[SpaceGraph] ما بيتغيرش
 * لما منطقة تحتاج حاجة جديدة (المناطق بتتبني بالتوازي — كل واحدة في ملفها: `HomeGraph.kt` · `PeopleGraph.kt` …).
 * - [repos]: مستودعات البلد الشغالة (واجهات بس — فايربيز على الجوال والذاكرة في الاختبار).
 * - [env]: اللي من الجهاز (الوقت · المعرّفات · تعلّم التنبيهات · النت · نسخة الملفات).
 * - [shell]: حالات استخدام الهيكل المشتركة (`addTransaction` · `engine` محرك التنبيهات · `profile`) — استعملها بدل ما تبني نسخة تانية.
 * - [feeds]: ملفات الأسعار والمتوسطات (مشتركة بين البلاد). [session]: الحساب والبلاد المفتوحة.
 * محتاج حاجة من الجهاز مش هنا (صندوق الرسايل · قارئ PDF · الملفات)؟ ضيفها في [DeviceEnv] (قرار مكتوب في ARCHITECTURE §31.31).
 */
class AreaContext(
    val space: Space,
    val repos: SpaceRepositories,
    val env: DeviceEnv,
    val session: SessionLinks,
    val feeds: LoadOnlineFeeds,
    val shell: ShellGraph,
)
