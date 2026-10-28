package com.comicrack.data.mappers

import com.comicrack.data.database.entities.LibraryPath
import com.comicrack.domain.models.Library

/**
 * Convert database entity to domain model.
 */
fun LibraryPath.toDomain(): Library =
    Library(
        id = id,
        path = path,
        displayName = displayName,
        createdAt = createdAt,
        lastScannedAt = lastScannedAt,
        comicCount = comicCount,
        isActive = isActive,
    )

/**
 * Convert domain model to database entity.
 */
fun Library.toEntity(): LibraryPath =
    LibraryPath(
        id = id,
        path = path,
        displayName = displayName,
        createdAt = createdAt,
        lastScannedAt = lastScannedAt,
        comicCount = comicCount,
        isActive = isActive,
    )
