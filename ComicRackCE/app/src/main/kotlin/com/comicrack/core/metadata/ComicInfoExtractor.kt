package com.comicrack.core.metadata

import android.content.Context
import android.net.Uri
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Extractor for ComicInfo.xml files within comic archives (ZIP, CBZ, etc.).
 */
class ComicInfoExtractor(
    private val archiveStreamFactory: ArchiveStreamFactory
) : MetadataExtractor {

    companion object {
        private const val TAG = "ComicInfoExtractor"
        private const val COMICINFO_FILENAME = "ComicInfo.xml"

        // Canonical XML tag names from ComicInfo v2.0 standard
        private val COMICINFO_FIELDS = setOf(
            "Title", "Series", "Number", "Count", "Volume", "StoryArc", "SeriesGroup",
            "Summary", "Notes", "Year", "Month", "Day", "Writer", "Penciller", "Inker",
            "Colorist", "Letterer", "CoverArtist", "Editor", "Publisher", "Imprint",
            "Genre", "Web", "PageCount", "LanguageISO", "Format", "BlackAndWhite",
            "Manga", "Characters", "Teams", "Locations", "ScanInformation",
            "CommunityRating", "MainCharacterOrTeam", "AlternateSeries",
            "AlternateNumber", "AlternateCount", "AgeRating"
        )

        private val LIST_FIELDS = setOf(
            "Writer", "Penciller", "Inker", "Colorist", "Letterer",
            "CoverArtist", "Editor", "Genre", "Characters", "Teams", "Locations"
        )
    }

    override suspend fun extract(uri: Uri): Map<String, String?>? {
        Timber.tag(TAG).d("Extracting metadata for: $uri")
        
        // Determine format from URI/FileName (simplified for now)
        val format = uri.path?.substringAfterLast('.', "") ?: "cbz"
        
        return archiveStreamFactory.openFileStream(uri, COMICINFO_FILENAME, format)?.use { input ->
            parseXmlStream(input)
        }
    }

    override fun getSourceFormat(): String = "ComicInfo"

    private fun parseXmlStream(inputStream: InputStream): Map<String, String?> {
        val fields = mutableMapOf<String, String?>()
        val moshi = Moshi.Builder().build()
        val listAdapter = moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))

        try {
            InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = false
                val parser = factory.newPullParser()
                parser.setInput(reader)

                var eventType = parser.eventType
                var currentTag: String? = null
                val tagStack = mutableListOf<String>()

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            tagStack.add(currentTag)
                        }
                        XmlPullParser.TEXT -> {
                            val text = parser.text?.trim() ?: ""
                            if (text.isNotEmpty() && currentTag != null && currentTag in COMICINFO_FIELDS) {
                                val normalizedFieldName = normalizeTagName(currentTag)
                                
                                if (LIST_FIELDS.contains(currentTag)) {
                                    val existingList =
                                        resultToList(fields[normalizedFieldName], listAdapter)
                                    val newItems = text.split(Regex("[,|]"))
                                        .map { it.trim() }.filter { it.isNotEmpty() }
                                    existingList.addAll(newItems)
                                    fields[normalizedFieldName] =
                                        listAdapter.toJson(existingList.distinct())
                                } else {
                                    fields[normalizedFieldName] = text
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (tagStack.isNotEmpty()) tagStack.removeAt(tagStack.size - 1)
                            currentTag = if (tagStack.isEmpty()) null else tagStack.last()
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error parsing XML stream")
        }
        return fields
    }

    private fun normalizeTagName(tag: String): String {
        return when (tag) {
            "Penciller" -> "penciller"
            "CoverArtist" -> "coverArtist"
            else -> tag.replaceFirstChar { it.lowercase() }
        }
    }

    private fun resultToList(
        json: String?,
        adapter: com.squareup.moshi.JsonAdapter<List<String>>,
    ): MutableList<String> {
        if (json == null) return mutableListOf()
        return try {
            adapter.fromJson(json)?.toMutableList() ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }
}
