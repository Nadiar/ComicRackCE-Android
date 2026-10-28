package com.comicrack.remote.cache

/**
 * Configuration for remote comic cache behavior.
 *
 * Defines cache size limits, storage paths, and eviction policies.
 * All paths are relative to app's cache directory (app-private, auto-cleared on uninstall).
 *
 * Phase 3: Caching & Download Management
 * Remote Library module - Cache configuration
 */
data class CacheConfig(
    /**
     * Maximum total cache size in bytes.
     * When exceeded, LRU eviction removes oldest accessed files.
     * Default: 500 MB (adjust for devices with limited storage).
     */
    val maxCacheSizeBytes: Long = 500L * 1024 * 1024,

    /**
     * Maximum thumbnail cache size in bytes.
     * Thumbnails are small, so this is typically much smaller than file cache.
     * Default: 50 MB.
     */
    val maxThumbnailCacheSizeBytes: Long = 50L * 1024 * 1024,

    /**
     * Subdirectory name for cached comic files.
     * Relative to context.cacheDir.
     */
    val fileCacheDir: String = "remote_comics",

    /**
     * Subdirectory name for cached thumbnails.
     * Relative to context.cacheDir.
     */
    val thumbnailCachePath: String = "remote_thumbnails",

    /**
     * Enable or disable automatic LRU eviction.
     * When true, cache is cleaned up automatically before downloads.
     * When false, eviction must be triggered manually.
     */
    val enableAutoEviction: Boolean = true,

    /**
     * Size threshold in bytes. When cache exceeds this size,
     * eviction is triggered to remove oldest files.
     * Typically slightly less than maxCacheSizeBytes to maintain headroom.
     */
    val evictionThresholdBytes: Long = 450L * 1024 * 1024
) {
    init {
        require(maxCacheSizeBytes > 0) { "maxCacheSizeBytes must be positive" }
        require(maxThumbnailCacheSizeBytes > 0) { "maxThumbnailCacheSizeBytes must be positive" }
        require(evictionThresholdBytes <= maxCacheSizeBytes) {
            "evictionThresholdBytes must not exceed maxCacheSizeBytes"
        }
        require(fileCacheDir.isNotEmpty()) { "fileCacheDir must not be empty" }
        require(thumbnailCachePath.isNotEmpty()) { "thumbnailCachePath must not be empty" }
    }

    companion object {
        /**
         * Create a CacheConfig with conservative settings for low-storage devices.
         * Limit to 250 MB total cache.
         */
        fun lowStorage(): CacheConfig = CacheConfig(
            maxCacheSizeBytes = 250L * 1024 * 1024,
            maxThumbnailCacheSizeBytes = 25L * 1024 * 1024,
            evictionThresholdBytes = 200L * 1024 * 1024
        )

        /**
         * Create a CacheConfig with generous settings for high-storage devices.
         * Limit to 1 GB total cache.
         */
        fun highStorage(): CacheConfig = CacheConfig(
            maxCacheSizeBytes = 1024L * 1024 * 1024,
            maxThumbnailCacheSizeBytes = 100L * 1024 * 1024,
            evictionThresholdBytes = 900L * 1024 * 1024
        )
    }
}
