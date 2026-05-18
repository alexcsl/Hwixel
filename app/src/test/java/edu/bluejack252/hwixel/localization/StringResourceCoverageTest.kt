package edu.bluejack252.hwixel.localization

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verifies that every string defined in the default (English) strings.xml has a
 * corresponding entry in the Bahasa Indonesia strings.xml (values-in/strings.xml).
 *
 * This test runs on the JVM during the unit-test phase and reads the XML files
 * directly from the source tree, so it does not need an Android device or emulator.
 */
class StringResourceCoverageTest {

    private val resDir = locateResDir()

    @Test
    fun allEnglishStringsHaveIndonesianTranslations() {
        val enStrings = parseStringNames(File(resDir, "values/strings.xml"))
        val idStrings = parseStringNames(File(resDir, "values-in/strings.xml"))

        val missing = enStrings - idStrings
        assertTrue(
            "The following ${missing.size} string(s) are defined in English but missing from " +
                "Bahasa Indonesia (values-in/strings.xml):\n" +
                missing.sorted().joinToString("\n") { "  - $it" },
            missing.isEmpty()
        )
    }

    @Test
    fun indonesianFileContainsNoOrphanedKeys() {
        val enStrings = parseStringNames(File(resDir, "values/strings.xml"))
        val idStrings = parseStringNames(File(resDir, "values-in/strings.xml"))

        val orphaned = idStrings - enStrings
        assertTrue(
            "The following ${orphaned.size} key(s) exist in values-in/strings.xml but not in " +
                "the default English file (values/strings.xml):\n" +
                orphaned.sorted().joinToString("\n") { "  - $it" },
            orphaned.isEmpty()
        )
    }

    @Test
    fun localesConfigDeclaresEnglishAndIndonesian() {
        val localesConfigFile = File(resDir, "xml/locales_config.xml")
        assertTrue(
            "res/xml/locales_config.xml must exist for Android 13+ per-app language support",
            localesConfigFile.exists()
        )
        val content = localesConfigFile.readText()
        assertTrue(
            "locales_config.xml must declare the English locale",
            content.contains("android:name=\"en\"")
        )
        assertTrue(
            "locales_config.xml must declare the Indonesian locale",
            content.contains("android:name=\"id\"")
        )
    }

    private fun parseStringNames(file: File): Set<String> {
        if (!file.exists()) return emptySet()
        val doc: Document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        doc.documentElement.normalize()
        val names = mutableSetOf<String>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val name = strings.item(i).attributes.getNamedItem("name")?.nodeValue
            if (name != null) names += name
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val name = plurals.item(i).attributes.getNamedItem("name")?.nodeValue
            if (name != null) names += name
        }
        return names
    }

    private fun locateResDir(): File {
        val candidates = listOf(
            File("app/src/main/res"),
            File("../app/src/main/res"),
            File("../../app/src/main/res"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate app/src/main/res. Run tests from the project root directory.")
    }
}
