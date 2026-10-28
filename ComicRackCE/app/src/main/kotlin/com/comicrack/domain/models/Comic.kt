package com.comicrack.domain.models

/**
 * Domain model for a comic book.
 *
 * This is an immutable representation of a comic book in the domain layer,
 * independent of database or UI concerns.
 */
data class Comic(
    val id: Long,
    val title: String,
    val filePath: String,
    val fileName: String,
    val series: String,
    val issueNumber: String,
    val pageCount: Int,
    val currentPage: Int,
    val lastPageRead: Int,
    val format: String,
    val fileSize: Long,
    val dateModified: Long,
    val metadata: ComicMetadata? = null,
)

/**
 * Metadata information for a comic book.
 *
 * Separated from the core Comic model to keep it lean and allow
 * lazy loading of metadata.
 */
data class ComicMetadata(
    val writer: String? = null,
    val penciller: String? = null,
    val inker: String? = null,
    val colorist: String? = null,
    val publisher: String? = null,
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
    val genre: String? = null,
    val ageRating: String? = null,
    val rating: Int = 0,
    val communityRating: Float = 0f,
    val manga: Boolean = false,
    val blackAndWhite: Boolean = false,
)
