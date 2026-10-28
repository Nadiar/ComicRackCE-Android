package com.comicrack.core.metadata

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.comicrack.core.storage.saf.SAFFileSystem
import okio.Path.Companion.toPath
import timber.log.Timber
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Extractor for .cbp.xml sidecar metadata files used by WiFi sync.
 */
class CbpMetadataExtractor(
    private val context: Context
) : MetadataExtractor {

    companion object {
        private const val XML_EXTENSION = ".xml"
        
        // Field mapping from CBP XML to internal names
        private val CBP_FIELD_MAPPING = mapOf(
            "Title" to "title",
            "Series" to "series",
            "Number" to "number",
            "Count" to "count",
            "Volume" to "volume",
            "Summary" to "summary",
            "Writer" to "writer",
            "Publisher" to "publisher",
            "Genre" to "genre",
            "Year" to "year",
            "Month" to "month",
            "Day" to "day",
            "LanguageISO" to "languageiso",
            "PageCount" to "pagecount",
            "Format" to "format",
            "AgeRating" to "agerating"
        )
    }

    override suspend fun extract(uri: Uri): Map<String, String?>? {
        // For CBP, we usually look for a sidecar file in the _sync folder
        // The implementation here will match the logic in SyncMetadataService
        // but focused on extraction.
        
        // Note: This extractor expects the URI to be the COMIC file URI.
        // It will then attempt to find the sidecar.
        
        return try {
            val fileName = getFileName(uri) ?: return null
            val parentUri = getParentUri(uri) ?: return null
            
            val fs = SAFFileSystem(context, parentUri)
            val xmlPath = "_sync".toPath() / "$fileName$XML_EXTENSION"
            
            if (!fs.exists(xmlPath)) {
                Timber.tag("CbpMetadataExtractor").d("Sidecar XML not found: $xmlPath")
                return null
            }
            
            val xmlBytes = fs.read(xmlPath) { readByteArray() }
            parseCbpXml(xmlBytes)
        } catch (e: Exception) {
            Timber.tag("CbpMetadataExtractor").e(e, "Failed to extract CBP metadata")
            null
        }
    }

    override fun getSourceFormat(): String = "CBP"

    private fun parseCbpXml(xmlBytes: ByteArray): Map<String, String?> {
        val fields = mutableMapOf<String, String?>()
        try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(ByteArrayInputStream(xmlBytes))
            val root = doc.documentElement
            root.normalize()

            CBP_FIELD_MAPPING.forEach { (xmlTag, internalName) ->
                val nodes = root.getElementsByTagName(xmlTag)
                if (nodes.length > 0) {
                    fields[internalName] = nodes.item(0).textContent?.trim()
                }
            }
        } catch (e: Exception) {
            Timber.tag("CbpMetadataExtractor").e(e, "Error parsing CBP XML")
        }
        return fields
    }

    private fun getFileName(uri: Uri): String? {
        val doc = DocumentFile.fromSingleUri(context, uri)
        return doc?.name
    }
    
    private fun getParentUri(uri: Uri): Uri? {
        // This is a bit of a hack for SAF, ideally we'd have the library path URI
        // But we can try to get it from the document URI
        val doc = DocumentFile.fromSingleUri(context, uri)
        return doc?.parentFile?.uri
    }
}
