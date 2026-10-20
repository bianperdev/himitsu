package me.kafuuneko.rpclient.libs.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Himitsu (P-03): nothing may leave the device through Android backups, so every rule set must
 * exclude every domain and include nothing.
 */
class RequestLogBackupRulesTest {
    @Test
    fun backupRules_excludeEveryDomain() {
        val root = parseXml("backup_rules.xml")

        assertEquals(AllDomains, root.getElementsByTagName("exclude").asElements().excludedDomains())
        assertTrue(root.getElementsByTagName("include").length == 0)
    }

    @Test
    fun dataExtractionRules_excludeEveryDomainFromCloudBackupAndDeviceTransfer() {
        val root = parseXml("data_extraction_rules.xml")
        val cloud = root.getElementsByTagName("cloud-backup").item(0) as Element
        val transfer = root.getElementsByTagName("device-transfer").item(0) as Element

        assertEquals(AllDomains, cloud.getElementsByTagName("exclude").asElements().excludedDomains())
        assertEquals(AllDomains, transfer.getElementsByTagName("exclude").asElements().excludedDomains())
        assertTrue(root.getElementsByTagName("include").length == 0)
    }

    private fun parseXml(name: String): Element {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val candidates = listOf(
            workingDirectory.resolve("src/main/res/xml/$name"),
            workingDirectory.resolve("app/src/main/res/xml/$name")
        )
        val source = candidates.firstOrNull(File::isFile)
            ?: error("Cannot locate $name from $workingDirectory")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source).documentElement
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> {
        return (0 until length).map { item(it) as Element }
    }

    /** Domains excluded as a whole (path "."). */
    private fun List<Element>.excludedDomains(): Set<String> {
        return filter { it.getAttribute("path") == "." }.mapTo(mutableSetOf()) { it.getAttribute("domain") }
    }

    private companion object {
        val AllDomains = setOf("root", "file", "database", "sharedpref", "external")
    }
}
