package com.zurus.cinethread.core.common

import javax.inject.Qualifier

/** Диспетчер для IO-операций: сеть, база данных. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Диспетчер для UI: корутины на главном потоке. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/** Диспетчер для CPU-нагрузок: BFS, генерация пар, валидация цепочки. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
