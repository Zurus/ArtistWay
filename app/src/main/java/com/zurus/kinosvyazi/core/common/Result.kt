package com.zurus.kinosvyazi.core.common

/**
 * Результат операции, способной завершиться ошибкой.
 *
 * Используется на границах слоёв: репозитории возвращают
 * `Result<T, AppError>` (docs/02_architecture.md, docs/03_data_layer.md),
 * доменная логика оперирует значениями.
 */
sealed interface Result<out T, out E> {

    /** `true`, если операция завершилась успешно. */
    val isSuccess: Boolean get() = this is Success

    /** Успех: содержит значение. */
    data class Success<out T>(val value: T) : Result<T, Nothing>

    /** Ошибка: содержит причину. */
    data class Failure<out E>(val error: E) : Result<Nothing, E>

    /** Значение при успехе, иначе `null`. */
    fun getOrNull(): T? = (this as? Success)?.value

    /** Ошибка при провале, иначе `null`. */
    fun errorOrNull(): E? = (this as? Failure)?.error

    /** Преобразует значение при успехе, ошибку — оставляет как есть. */
    fun <R> map(transform: (T) -> R): Result<R, E> =
        when (this) {
            is Success -> Success(transform(value))
            is Failure -> this
        }

    /** Вызывает [onSuccess] при успехе или [onFailure] при ошибке. */
    fun fold(onSuccess: (T) -> Unit, onFailure: (E) -> Unit) {
        when (this) {
            is Success -> onSuccess(value)
            is Failure -> onFailure(error)
        }
    }

    companion object {
        fun <T> success(value: T): Result<T, AppError> = Success(value)
        fun <E> failure(error: E): Result<Nothing, E> = Failure(error)

        /**
         * Выполняет [block], оборачивая результат в [Success]
         * и ошибки — в [Failure] через [transform].
         *
         * Намеренно ловит `Throwable`: утилита перевода произвольных
         * исключений данных-слоя в типизированные ошибки домена.
         */
        @Suppress("TooGenericExceptionCaught")
        fun <T, E> fromThrowable(
            transform: (Throwable) -> E,
            block: () -> T,
        ): Result<T, E> =
            try {
                Success(block())
            } catch (throwable: Throwable) {
                Failure(transform(throwable))
            }
    }
}
