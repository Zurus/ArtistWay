package com.zurus.kinosvyazi.data.mapper

import com.zurus.kinosvyazi.data.local.entity.ActorEntity
import com.zurus.kinosvyazi.data.local.entity.ChainStepEntity
import com.zurus.kinosvyazi.data.local.entity.GameSessionEntity
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.GameSession
import com.zurus.kinosvyazi.domain.model.GameSessionStatus
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Маппинг партии (docs/03_data_layer.md §4):
 * `GameSession` ↔ `GameSessionEntity` + `ChainStepEntity`.
 * Шаги восстанавливаются в порядке `stepIndex`.
 *
 * Отклонения от доков (доменная модель не несёт `sessionId`,
 * а маппер не имеет доступа к Room):
 * - `toEntity` принимает [sessionId] и время сохранения отдельно;
 * - `toDomain` принимает актёрские строки (стартовый/целевой/текущий)
 *   и базовый URL изображений.
 */
interface GameSessionMapper {

    fun toEntity(session: GameSession, sessionId: String, fetchedAtMillis: Long): GameSessionEntity

    fun toDomain(
        sessionEntity: GameSessionEntity,
        startActor: ActorEntity,
        targetActor: ActorEntity,
        currentActor: ActorEntity,
        steps: List<ChainStepEntity>,
        imageBaseUrl: String,
    ): GameSession

    class Default @Inject constructor(
        private val personMapper: PersonMapper,
    ) : GameSessionMapper {

        override fun toEntity(
            session: GameSession,
            sessionId: String,
            fetchedAtMillis: Long,
        ): GameSessionEntity =
            GameSessionEntity(
                sessionId = sessionId,
                startPersonId = session.startActor.tmdbId,
                targetPersonId = session.targetActor.tmdbId,
                currentPersonId = session.currentActor.tmdbId,
                status = session.status.name,
                startedAtMillis = session.startedAtMillis,
                updatedAtMillis = fetchedAtMillis,
                finishedAtMillis = session.finishedAtMillis,
                stepCount = session.stepCount,
                shortestPathJson = session.shortestPath?.let { steps ->
                    json.encodeToString(steps.map { step -> step.toDto() })
                },
                generationSeed = session.generationSeed,
            )

        override fun toDomain(
            sessionEntity: GameSessionEntity,
            startActor: ActorEntity,
            targetActor: ActorEntity,
            currentActor: ActorEntity,
            steps: List<ChainStepEntity>,
            imageBaseUrl: String,
        ): GameSession =
            GameSession(
                startActor = personMapper.toDomain(startActor, imageBaseUrl),
                targetActor = personMapper.toDomain(targetActor, imageBaseUrl),
                currentActor = personMapper.toDomain(currentActor, imageBaseUrl),
                chain = steps.sortedBy { it.stepIndex }.map { it.toDomain() },
                status = sessionEntity.status.toDomainStatus(),
                startedAtMillis = sessionEntity.startedAtMillis,
                updatedAtMillis = sessionEntity.updatedAtMillis,
                finishedAtMillis = sessionEntity.finishedAtMillis,
                stepCount = sessionEntity.stepCount,
                shortestPath = sessionEntity.shortestPathJson?.let { raw ->
                    decodeShortestPath(raw)
                },
                generationSeed = sessionEntity.generationSeed,
            )

        private fun decodeShortestPath(raw: String): List<ChainStep>? =
            try {
                json.decodeFromString<List<ChainStepJsonDto>>(raw).map { it.toDomain() }
            } catch (e: SerializationException) {
                Timber.e(e, "Не удалось разобрать shortestPathJson, игнорирую гарантированное решение")
                null
            }

        private fun String.toDomainStatus(): GameSessionStatus =
            runCatching { GameSessionStatus.valueOf(this) }
                .getOrElse {
                    Timber.e("Неизвестный статус партии: $this, считаю ERROR")
                    GameSessionStatus.ERROR
                }

        private fun ChainStepEntity.toDomain() =
            ChainStep(
                stepIndex = stepIndex,
                fromActorId = fromPersonId,
                movieId = movieId,
                toActorId = toPersonId,
            )

        private fun ChainStep.toDto() =
            ChainStepJsonDto(
                stepIndex = stepIndex,
                fromActorId = fromActorId,
                movieId = movieId,
                toActorId = toActorId,
            )

        private fun ChainStepJsonDto.toDomain() =
            ChainStep(
                stepIndex = stepIndex,
                fromActorId = fromActorId,
                movieId = movieId,
                toActorId = toActorId,
            )
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

/**
 * Сериализуемая форма шага для `shortestPathJson` (docs/03_data_layer.md §2:
 * «формат сериализации фиксируется на этапе реализации»).
 */
@Serializable
internal data class ChainStepJsonDto(
    val stepIndex: Int,
    val fromActorId: Int,
    val movieId: Int,
    val toActorId: Int,
)
