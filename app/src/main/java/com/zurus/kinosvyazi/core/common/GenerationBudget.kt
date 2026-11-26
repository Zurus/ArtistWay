package com.zurus.kinosvyazi.core.common

import javax.inject.Qualifier

/**
 * Бюджетный профиль генерации пары (docs/04_algorithms.md §5.4, docs/debugging.md D1).
 *
 * Без квалификатора — строгий дефолтный профиль ([com.zurus.kinosvyazi.domain.util.Budget]
 * с `DEFAULT_*` лимитами): справочные проверки во время партии
 * (`CheckDeadEndUseCase`) не должны подвешивать геймплей надолго.
 * С квалификатором — щадящий профиль для `generatePair`: реальный
 * движок работает последовательно, ~1 запрос/с (см. логи в docs/debugging.md),
 * поэтому wall-clock должен покрывать десятки реальных запросов.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GenerationBudget
