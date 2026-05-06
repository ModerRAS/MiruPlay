package com.miruplay.tv.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class GenreListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromGenreList(genres: List<String>?): String? {
        return genres?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toGenreList(value: String?): List<String>? {
        return value?.let { json.decodeFromString(it) }
    }
}
