package com.zurus.kinosvyazi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zurus.kinosvyazi.R
import com.zurus.kinosvyazi.domain.model.Movie

/**
 * Карточка фильма в списке (docs/05_roadmap.md, пункт 4, компонент MovieCard):
 * постер (если есть), название, год.
 */
@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoviePoster(movie.posterUrl, modifier = Modifier.size(width = 44.dp, height = 64.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = movie.year?.toString() ?: stringResource(R.string.movie_picker_unknown_year),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Постер фильма; без URL — нейтральная заглушка.
 */
@Composable
fun MoviePoster(
    posterUrl: String?,
    modifier: Modifier = Modifier,
) {
    if (posterUrl != null) {
        AsyncImage(
            model = posterUrl,
            contentDescription = stringResource(R.string.content_description_poster),
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // Пустая заглушка для фильма без постера.
        }
    }
}
