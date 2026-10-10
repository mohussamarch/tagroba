package app.masroufy.data

import java.io.File

/**
 * ملف تقرير اختبار تحت `build/` جنب الموديول — **بيعمل المجلد لو مش موجود** (زي `core`: ملفات البناء على قرص تاني من commit 2c58905،
 * فمجلد `build/` ما بقاش بيتعمل لوحده والكتابة كانت بتفشل في أي نسخة جديدة).
 */
fun reportFile(name: String): File = File("build").apply { mkdirs() }.resolve(name)
