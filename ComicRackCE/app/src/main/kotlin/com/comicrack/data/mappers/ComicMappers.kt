package com.comicrack.data.mappers

import com.comicrack.data.database.entities.ComicBook
import com.comicrack.domain.models.Comic
import com.comicrack.domain.models.ComicMetadata

/**
 * Convert database entity to domain model.
 */
fun ComicBook.toDomain(): Comic =
    Comic(
        id = id,
        title = title,
        filePath = filePath,
        fileName = fileName,
        series = series,
        issueNumber = issueNumber,
        pageCount = pageCount,
        currentPage = currentPage,
        lastPageRead = lastPageRead,
        format = format,
        fileSize = fileSize,
        dateModified = dateModified,
        metadata = toMetadata(),
    )

/**
 * Convert database entity metadata fields to domain metadata model.
 */
fun ComicBook.toMetadata(): ComicMetadata? {
    // Only create metadata object if at least one field is non-null/non-zero
    val hasMetadata =
        writer != null ||
            penciller != null ||
            inker != null ||
            colorist != null ||
            publisher != null ||
            year != 0 ||
            genre != null ||
            ageRating != null ||
            rating != 0 ||
            communityRating != 0f ||
            manga ||
            blackAndWhite

    return if (hasMetadata) {
        ComicMetadata(
            writer = writer,
            penciller = penciller,
            inker = inker,
            colorist = colorist,
            publisher = publisher,
            year = year,
            month = month,
            day = day,
            genre = genre,
            ageRating = ageRating,
            rating = rating,
            communityRating = communityRating,
            manga = manga,
            blackAndWhite = blackAndWhite,
        )
    } else {
        null
    }
}

/**
 * Convert domain model to database entity.
 * Requires libraryPathId which is not part of the domain model.
 */
fun Comic.toEntity(libraryPathId: Long): ComicBook =
    ComicBook(
        id = id,
        libraryPathId = libraryPathId,
        title = title,
        filePath = filePath,
        fileName = fileName,
        series = series,
        issueNumber = issueNumber,
        pageCount = pageCount,
        currentPage = currentPage,
        lastPageRead = lastPageRead,
        format = format,
        fileSize = fileSize,
        dateModified = dateModified,
        writer = metadata?.writer,
        penciller = metadata?.penciller,
        inker = metadata?.inker,
        colorist = metadata?.colorist,
        publisher = metadata?.publisher,
        year = metadata?.year ?: 0,
        month = metadata?.month ?: 0,
        day = metadata?.day ?: 0,
        genre = metadata?.genre,
        ageRating = metadata?.ageRating,
        rating = metadata?.rating ?: 0,
        communityRating = metadata?.communityRating ?: 0f,
        manga = metadata?.manga ?: false,
        blackAndWhite = metadata?.blackAndWhite ?: false,
    )
