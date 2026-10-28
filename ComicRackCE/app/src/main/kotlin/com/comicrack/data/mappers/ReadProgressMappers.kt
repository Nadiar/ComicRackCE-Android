package com.comicrack.data.mappers

import com.comicrack.data.database.entities.ReadProgress
import com.comicrack.domain.models.ReadingProgress

/**
 * Convert database entity to domain model.
 */
fun ReadProgress.toDomain(): ReadingProgress =
    ReadingProgress(
        comicId = comicId,
        currentPage = currentPage,
        totalPages = totalPages,
        lastReadTime = lastReadTime,
        isRead = isRead,
    )

/**
 * Convert domain model to database entity.
 */
fun ReadingProgress.toEntity(): ReadProgress =
    ReadProgress(
        id = 0, // Let Room auto-generate
        comicId = comicId,
        currentPage = currentPage,
        totalPages = totalPages,
        lastReadTime = lastReadTime,
        isRead = isRead,
    )
