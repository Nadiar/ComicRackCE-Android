package com.comicrack.wifi.metadata

import android.content.Context
import android.net.Uri
import com.comicrack.data.database.entities.ComicBook
import com.comicrack.data.database.entities.LibraryPath
import com.comicrack.core.storage.saf.SAFFileSystem
import okio.Path.Companion.toPath
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Service for managing .cbp.xml sidecar metadata files used by WiFi sync.
 *
 * This service handles reading and updating reading state (read/unread, current page)
 * in the sidecar XML files to ensure that the Desktop client can correctly
 * synchronize the reading progress from the Android device.
 *
 * Tiered Logic:
 * 1. For .cbp (WiFi Sync) files only.
 * 2. Updates <Opened>, <OpenCount>, <CurrentPage>, <LastPageRead>.
 * 3. Sets <ComicInfoIsDirty> to true.
 * 4. Ensures <ExtraSyncInformation><ReadingStateChanged> is true.
 */
class SyncMetadataService(
    private val context: Context,
) {
    companion object {
        private const val TAG = "SyncMetadataService"
        private const val CBP_EXTENSION = "cbp"
        private const val XML_EXTENSION = ".xml"
    }

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    /**
     * Update the read status in the sidecar .cbp.xml file if it exists.
     *
     * @param comic The comic book entity
     * @param libraryPath The LibraryPath entity where the comic belongs
     * @param isRead Whether the comic is marked as read
     * @param currentPage The current page being read
     * @param lastPageRead The last page read
     */
    suspend fun updateSyncReadStatus(
        comic: ComicBook,
        libraryPath: LibraryPath,
        isRead: Boolean,
        currentPage: Int,
        lastPageRead: Int
    ) {
        if (!isSyncFile(comic)) {
            return
        }

        try {
            val rootUri = Uri.parse(libraryPath.path)
            val fs = SAFFileSystem(context, rootUri)
            
            // Sidecar XML is always in the _sync folder relative to library root
            val xmlPath = "_sync".toPath() / "${comic.fileName}$XML_EXTENSION"

            // Check if sidecar XML exists
            if (!fs.exists(xmlPath)) {
                Timber.tag(TAG).d("Sidecar XML not found: $xmlPath")
                return
            }

            // Read existing XML
            val xmlBytes = try {
                fs.read(xmlPath) {
                    readByteArray()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to read sidecar XML: $xmlPath")
                return
            }

            // Parse and update
            val updatedXml = updateXmlMetadata(xmlBytes, isRead, currentPage, lastPageRead) ?: return

            // Write back
            try {
                fs.write(xmlPath, mustCreate = false) {
                    write(updatedXml)
                }
                Timber.tag(TAG).i(
                    "Updated sidecar XML reading status: ${comic.fileName} " +
                        "(isRead=$isRead, page=$currentPage)"
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to write sidecar XML: $xmlPath")
            }

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating sync metadata for: ${comic.fileName}")
        }
    }

    private fun isSyncFile(comic: ComicBook): Boolean {
        return comic.format.lowercase() == CBP_EXTENSION
    }

    private fun updateXmlMetadata(
        xmlBytes: ByteArray,
        _isRead: Boolean,
        currentPage: Int,
        lastPageRead: Int
    ): ByteArray? {
        return try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(ByteArrayInputStream(xmlBytes))
            val root = doc.documentElement
            root.normalize()

            // 1. Reading Progress Fields
            updateOrAddElement(doc, root, "Opened", isoDateFormat.format(Date()))
            
            // Increment OpenCount if it exists, otherwise set to 1
            val openCountNodes = root.getElementsByTagName("OpenCount")
            if (openCountNodes.length > 0) {
                val currentCount = openCountNodes.item(0).textContent.toIntOrNull() ?: 0
                openCountNodes.item(0).textContent = (currentCount + 1).toString()
            } else {
                updateOrAddElement(doc, root, "OpenCount", "1")
            }

            updateOrAddElement(doc, root, "CurrentPage", currentPage.toString())
            updateOrAddElement(doc, root, "LastPageRead", lastPageRead.toString())

            // 2. Set Sync Flags
            updateOrAddElement(doc, root, "ComicInfoIsDirty", "true")

            // 3. ExtraSyncInformation
            var extraSyncInfo = root.getElementsByTagName("ExtraSyncInformation").item(0) as? Element
            if (extraSyncInfo == null) {
                extraSyncInfo = doc.createElement("ExtraSyncInformation")
                root.appendChild(extraSyncInfo)
            }
            updateOrAddElement(doc, extraSyncInfo, "ReadingStateChanged", "true")

            // Convert back to string
            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            
            // compact format (no extra whitespace)
            transformer.setOutputProperty(OutputKeys.INDENT, "no")
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            
            val out = ByteArrayOutputStream()
            transformer.transform(DOMSource(doc), StreamResult(out))
            out.toByteArray()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse/update XML content")
            null
        }
    }

    private fun updateOrAddElement(doc: Document, parent: Element, tagName: String, value: String) {
        val nodes = parent.getElementsByTagName(tagName)
        if (nodes.length > 0) {
            nodes.item(0).textContent = value
        } else {
            val element = doc.createElement(tagName)
            element.textContent = value
            parent.appendChild(element)
        }
    }
}
