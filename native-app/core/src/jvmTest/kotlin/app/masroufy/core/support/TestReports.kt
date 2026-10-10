package app.masroufy.core

import java.io.File

/**
 * ملف تقرير اختبار تحت `build/` جنب الموديول — **بيعمل المجلد لو مش موجود**. من ساعة ما ملفات البناء اتنقلت لقرص تاني
 * (`masroufy.buildRoot` — commit 2c58905) مجلد `build/` جوه الموديول ما بقاش بيتعمل لوحده، فكتابة التقرير كانت بتفشل في أي نسخة
 * (worktree) جديدة بـ`FileNotFoundException` وهي مش مشكلة في الكود اللي بيتختبر.
 */
fun reportFile(name: String): File = File("build").apply { mkdirs() }.resolve(name)
