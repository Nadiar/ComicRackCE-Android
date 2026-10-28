package com.comicrack.settings.data

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified TagsRepository - Language-aware tags loader for settings catalog.
 *
 * Loads searchable tags from JSON files organized by language.
 * Falls back to English if language-specific file not found.
 *
 * Consolidated from catalog and data packages with unified API supporting:
 * - getTags(settingId) - Get tags for current device language
 * - getTagsForLanguage(languageCode) - Get all tags map for a language
 * - getTagsForSetting(settingId) / getTagsForSetting(settingId, languageCode) - Alternative APIs
 * - getAllTagsForLanguage(languageCode) - Get unique tags across all settings
 * - getAllTagsForCurrentLanguage() - Get unique tags for current language
 * - clearCache() - Clear cached data (useful for testing or language changes)
 *
 * Example structure (settings_tags_en.json):
 * ```json
 * {
 *   "rtl_mode": ["RTL", "LTR", "Reading", "Manga", "Arabic", "Hebrew"],
 *   "brightness": ["Screen", "Display", "Brightness"],
 *   "page_transition_effect": ["Navigation", "Animation", "Smooth"]
 * }
 * ```
 *
 * Missing entries return empty list, so JSON only needs settings with tags.
 *
 * Tags are cached in memory after first load and reused for the lifetime of the app.
 * Phase 2.0 uses device language (Locale.getDefault()).
 * Phase 3+: Add in-app language override support and cache invalidation.
 *   Future enhancements:
 *   - Expose an API to set a language override and persist it in DataStore.
 *   - When override changes, clear the in-memory cache via `clearCache()`.
 *   - Add unit tests for fallback behavior and resource loading.
 */
@Singleton
class TagsRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val moshi: Moshi,
    ) {
        private val tagCache = mutableMapOf<String, Map<String, List<String>>>()

        /**
         * Get tags for a specific setting in the current language.
         * Falls back to English if language-specific tags not found.
         * Returns empty list if setting has no tags.
         *
         * @param settingId Setting ID (e.g., "rtl_mode")
         * @return List of tags for this setting, or empty list if none defined
         */
        fun getTags(settingId: String): List<String> {
            val languageCode = getCurrentLanguageCode()
            val tagsMap = loadTagsForLanguage(languageCode)
            return tagsMap[settingId].orEmpty()
        }

        /**
         * Get all tags for a language.
         * @param languageCode Language code (e.g., "en", "ja", "es")
         * @return Map of setting ID to tags
         */
        fun getTagsForLanguage(languageCode: String): Map<String, List<String>> = loadTagsForLanguage(languageCode)

        /**
         * Load tags for a specific setting from the JSON file corresponding to the current language.
         *
         * @param settingId The setting ID (e.g., "rtl_mode")
         * @return List of tags for this setting, or empty list if not found
         */
        fun getTagsForSetting(settingId: String): List<String> {
            val locale = Locale.getDefault()
            val languageCode = locale.language // "en", "es", "fr", etc.
            return getTagsForSetting(settingId, languageCode)
        }

        /**
         * Load tags for a specific setting in a specific language.
         *
         * @param settingId The setting ID
         * @param languageCode The language code (e.g., "en", "es", "fr")
         * @return List of tags for this setting, or empty list if not found
         */
        fun getTagsForSetting(
            settingId: String,
            languageCode: String,
        ): List<String> {
            val tagsMap = loadTagsForLanguage(languageCode)
            return tagsMap[settingId].orEmpty()
        }

        /**
         * Get all unique tags across all settings for a language.
         *
         * @param languageCode The language code
         * @return Set of all unique tags
         */
        fun getAllTagsForLanguage(languageCode: String): Set<String> {
            val tagsMap = loadTagsForLanguage(languageCode)
            return tagsMap.values.flatten().toSet()
        }

        /**
         * Get all unique tags for the current device language.
         *
         * @return Set of all unique tags
         */
        fun getAllTagsForCurrentLanguage(): Set<String> {
            val languageCode = Locale.getDefault().language
            return getAllTagsForLanguage(languageCode)
        }

        private fun loadTagsForLanguage(languageCode: String): Map<String, List<String>> {
            // Check cache first
            val cached = tagCache[languageCode]
            if (cached != null) return cached

            // Try to load language-specific file from assets
            val fileName = "settings_tags_$languageCode.json"
            var tags = loadTagsFromAsset(fileName)

            // Fall back to raw resources if not in assets
            if (tags == null) {
                val resourceId = context.resources.getIdentifier(fileName, "raw", context.packageName)
                if (resourceId != 0) {
                    try {
                        val json =
                            context.resources.openRawResource(resourceId).use { inputStream ->
                                inputStream.bufferedReader().use { it.readText() }
                            }
                        val type = Types.newParameterizedType(Map::class.java, String::class.java, List::class.java)
                        val jsonAdapter = moshi.adapter<Map<String, List<String>>>(type)
                        tags = jsonAdapter.fromJson(json)
                    } catch (e: Exception) {
                        Timber.tag("TagsRepository").d("Could not load tags from raw resource $fileName: ${e.message}")
                        tags = null
                    }
                }
            }

            // Fall back to English if language not found
            if (tags == null && languageCode != "en") {
                tags = loadTagsForLanguage("en")
                if (tags.isEmpty()) {
                    Timber.tag("TagsRepository").w("No tags file found for language: $languageCode")
                    tags = emptyMap()
                }
            }

            // Cache result
            tagCache[languageCode] = tags.orEmpty()
            return tagCache[languageCode].orEmpty()
        }

        private fun loadTagsFromAsset(fileName: String): Map<String, List<String>>? =
            try {
                val jsonString =
                    context.assets.open(fileName).use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    }
                val mapType =
                    Types.newParameterizedType(
                        Map::class.java,
                        String::class.java,
                        Types.newParameterizedType(List::class.java, String::class.java),
                    )
                val adapter: JsonAdapter<Map<String, List<String>>> = moshi.adapter(mapType)
                adapter.fromJson(jsonString)
            } catch (e: Exception) {
                Timber.tag("TagsRepository").d("Could not load tags from $fileName: ${e.message}")
                null
            }

        private fun getCurrentLanguageCode(): String {
            val locale = Locale.getDefault()
            return locale.language // Returns "en", "ja", "es", "fr", etc.
        }

        /**
         * Clear cache (useful for testing or language changes).
         */
        fun clearCache() {
            tagCache.clear()
        }
    }
