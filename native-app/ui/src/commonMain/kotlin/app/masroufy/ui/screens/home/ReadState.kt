package app.masroufy.ui.screens.home

import kotlin.coroutines.cancellation.CancellationException

/**
 * قراية قايمة من حالة استخدام (التقويم · «القادم» في الرئيسية): بيحمّل · **فشلت** · جاهزة.
 * الفشل حالة لوحدها — **مش** قايمة فاضية: «لا أحداث قادمة» جملة مؤكدة، والقراية اللي فشلت مجهول (CLAUDE.md #10).
 */
sealed interface ReadState<out T> {
    data object Loading : ReadState<Nothing>

    data object Failed : ReadState<Nothing>

    data class Ready<T>(val value: T) : ReadState<T>
}

/** القيمة لو اتقرت، وإلا null (بيحمّل أو فشل) — اللي بيعرضها يفرّق بين الاتنين بنفسه. */
fun <T> ReadState<T>.valueOrNull(): T? = (this as? ReadState.Ready<T>)?.value

/**
 * نتيجة القراية ⇒ حالتها: أي خطأ ⇒ [ReadState.Failed]. الإلغاء (الشاشة اتقفلت أو المفتاح اتغير) بيعدّي زي ما هو — عشان قراية اتلغت
 * ما تكتبش «فشل» فوق القراية الجديدة.
 */
suspend fun <T> readState(read: suspend () -> T): ReadState<T> =
    try {
        ReadState.Ready(read())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ReadState.Failed
    }
