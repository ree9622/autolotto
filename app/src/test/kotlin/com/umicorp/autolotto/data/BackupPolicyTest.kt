package com.umicorp.autolotto.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/** Guard the Android 12+ transfer policy without requiring a device or credentials. */
class BackupPolicyTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"

    private fun readXml(path: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(File("src/main/$path"))

    @Test fun manifestKeepsBackupDisabledAndReferencesTransferRules() {
        val app = readXml("AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        assertEquals("false", app.getAttributeNS(androidNamespace, "allowBackup"))
        assertEquals("false", app.getAttributeNS(androidNamespace, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(androidNamespace, "dataExtractionRules"))
    }

    @Test fun cloudAndDeviceTransfersExcludeEveryStorageDomain() {
        val document = readXml("res/xml/data_extraction_rules.xml")
        assertEquals("data-extraction-rules", document.documentElement.tagName)
        assertEquals(0, document.getElementsByTagName("include").length)
        val domains = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
        for (mode in listOf("cloud-backup", "device-transfer")) {
            val sections = document.getElementsByTagName(mode)
            assertEquals("Exactly one $mode section is required", 1, sections.length)
            val exclusions = (sections.item(0) as Element).getElementsByTagName("exclude")
            val actual = (0 until exclusions.length).map {
                val entry = exclusions.item(it) as Element
                assertEquals("$mode must exclude the full domain", ".", entry.getAttribute("path"))
                entry.getAttribute("domain")
            }
            assertEquals("$mode must cover all storage domains", domains, actual.toSet())
            assertEquals("$mode must not repeat domains", domains.size, actual.size)
        }
    }
}
