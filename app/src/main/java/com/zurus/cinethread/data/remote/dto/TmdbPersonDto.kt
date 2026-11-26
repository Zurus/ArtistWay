package com.zurus.cinethread.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Данные актёра из `/person/{person_id}` (docs/03_data_layer.md §1).
 *
 * Все поля могут отсутствовать: валидность обеспечивает маппер,
 * обязательное поле для перехода — `id`.
 */
@Serializable
data class TmdbPersonDto(
    val id: Int? = null,
    val name: String? = null,
    @kotlinx.serialization.SerialName("profile_path")
    val profilePath: String? = null,
    @kotlinx.serialization.SerialName("known_for_department")
    val knownForDepartment: String? = null,
    val biography: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    val popularity: Double? = null,
)
