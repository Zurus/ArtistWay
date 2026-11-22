package com.zurus.kinosvyazi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zurus.kinosvyazi.R
import com.zurus.kinosvyazi.domain.model.Actor
import com.zurus.kinosvyazi.domain.model.ChainStep
import com.zurus.kinosvyazi.domain.model.Movie

/**
 * Читаемая цепочка `Старт → Фильм → Актёр → … → Цель`
 * (docs/05_roadmap.md, пункт 4, компонент ChainView;
 * docs/01_concept.md §2.11 — каждый переход отдельно).
 *
 * Имена резолвятся из карт [actors]/[movies]; для неизвестных ID
 * показываются заглушки вида «Актёр 12».
 *
 * @param currentActorId актёр, на котором стоит цепочка сейчас, или `null`
 *   (для завершённых партий).
 */
@Composable
fun ChainView(
    chain: List<ChainStep>,
    actors: Map<Int, Actor>,
    movies: Map<Int, Movie>,
    startActorId: Int,
    targetActorId: Int,
    modifier: Modifier = Modifier,
    currentActorId: Int? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ActorNode(
            actor = actors[startActorId],
            name = actors[startActorId]?.name ?: stringResource(R.string.common_unknown_actor, startActorId),
            mark = stringResource(R.string.chain_mark_start),
            current = startActorId == currentActorId,
        )
        chain.forEachIndexed { index, step ->
            MovieNode(
                movie = movies[step.movieId],
                title = movies[step.movieId]?.title
                    ?: stringResource(R.string.common_unknown_movie, step.movieId),
            )
            ActorNode(
                actor = actors[step.toActorId],
                name = actors[step.toActorId]?.name
                    ?: stringResource(R.string.common_unknown_actor, step.toActorId),
                mark = if (step.toActorId == targetActorId) {
                    stringResource(R.string.chain_mark_target)
                } else {
                    null
                },
                stepNumber = index + 1,
                current = step.toActorId == currentActorId,
            )
        }
    }
}

@Composable
private fun ActorNode(
    actor: Actor?,
    name: String,
    mark: String?,
    stepNumber: Int? = null,
    current: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (actor != null) {
            ActorPhoto(actor.photoUrl, modifier = Modifier.size(36.dp), sizeDp = 36)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (mark != null || current) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (stepNumber != null) {
                Text(
                    text = stringResource(R.string.chain_step_number, stepNumber),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (mark != null) {
            Text(
                text = mark,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MovieNode(movie: Movie?, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 18.dp),
    ) {
        Text(
            text = "▸",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        if (movie != null) {
            MoviePoster(movie.posterUrl, Modifier.size(width = 22.dp, height = 32.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
