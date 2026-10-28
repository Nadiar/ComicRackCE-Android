package com.comicrack.core.metadata

import com.comicrack.data.database.entities.ComicMetadata
import timber.log.Timber

/**
 * Utility for mapping raw metadata fields to ComicMetadata entity and performing normalization.
 */
class MetadataMapper {
    companion object {
        private val BOOLEAN_FIELDS = setOf("blackAndWhite", "manga")
        private val INTEGER_FIELDS = setOf("count", "volume", "year", "month", "day", "pagecount", "alternateCount")
        private val FLOAT_FIELDS = setOf("communityRating")
    }

    /**
     * Maps raw extracted fields to a ComicMetadata entity.
     * 
     * @param comicBookId The ID of the comic book.
     * @param defaultTitle A fallback title if none is found.
     * @param fields The raw extracted fields.
     * @param source The source format name.
     * @return A populated ComicMetadata entity.
     */
    fun mapToEntity(
        comicBookId: Long,
        defaultTitle: String,
        fields: Map<String, String?>,
        source: String
    ): ComicMetadata {
        val normalized = normalizeFields(fields)
        
        return ComicMetadata(
            comicBookId = comicBookId,
            title = normalized["title"] ?: defaultTitle,
            series = normalized["series"],
            number = normalized["number"],
            count = normalized["count"]?.toIntOrNull(),
            writers = normalized["writer"],
            pencillers = normalized["penciller"],
            inkers = normalized["inker"],
            colorists = normalized["colorist"],
            letterers = normalized["letterer"],
            coverArtists = normalized["coverArtist"],
            editors = normalized["editor"],
            publisher = normalized["publisher"],
            imprint = normalized["imprint"],
            genres = normalized["genre"],
            summary = normalized["summary"],
            notes = normalized["notes"],
            year = normalized["year"]?.toIntOrNull(),
            month = normalized["month"]?.toIntOrNull(),
            day = normalized["day"]?.toIntOrNull(),
            languageISO = normalized["languageiso"],
            format = normalized["format"],
            ageRating = normalized["agerating"],
            pageCount = normalized["pagecount"]?.toIntOrNull(),
            blackAndWhite = normalized["blackAndWhite"]?.toBoolean(),
            manga = normalized["manga"]?.toBoolean(),
            communityRating = normalized["communityRating"]?.toFloatOrNull(),
            characters = normalized["characters"],
            teams = normalized["teams"],
            locations = normalized["locations"],
            alternateSeries = normalized["alternateSeries"],
            alternateNumber = normalized["alternateNumber"],
            alternateCount = normalized["alternateCount"]?.toIntOrNull(),
            scanInformation = normalized["scanInformation"],
            mainCharacterOrTeam = normalized["mainCharacterOrTeam"],
            parsedAt = System.currentTimeMillis(),
            sourceFormat = source
        )
    }

    private fun normalizeFields(fields: Map<String, String?>): Map<String, String?> {
        val result = mutableMapOf<String, String?>()
        
        fields.forEach { (key, value) ->
            if (value.isNullOrBlank()) return@forEach
            
            val trimmed = value.trim()
            val lowerKey = key.lowercase()
            
            when {
                BOOLEAN_FIELDS.contains(lowerKey) -> {
                    result[key] = when (trimmed.lowercase()) {
                        "yes", "true", "1" -> "true"
                        "no", "false", "0" -> "false"
                        else -> trimmed
                    }
                }
                INTEGER_FIELDS.contains(lowerKey) -> {
                    val intVal = trimmed.toIntOrNull()
                    if (intVal != null) result[key] = intVal.toString()
                }
                FLOAT_FIELDS.contains(lowerKey) -> {
                    val floatVal = trimmed.toFloatOrNull()
                    if (floatVal != null) result[key] = floatVal.toString()
                }
                else -> result[key] = trimmed
            }
        }
        return result
    }
}
