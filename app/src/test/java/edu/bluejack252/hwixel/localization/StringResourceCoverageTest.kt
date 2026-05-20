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
        val doc = newSecureFactory().newDocumentBuilder().parse(localesConfigFile)
        doc.documentElement.normalize()
        val localeNodes = doc.getElementsByTagName("locale")
        val declaredNames = (0 until localeNodes.length).mapNotNull { i ->
            localeNodes.item(i).attributes.getNamedItem("android:name")?.nodeValue
        }.toSet()
        assertTrue(
            "locales_config.xml must declare the English locale (android:name=\"en\")",
            declaredNames.contains("en")
        )
        assertTrue(
            "locales_config.xml must declare the Indonesian locale (android:name=\"id\")",
            declaredNames.contains("id")
        )
    }

    private fun parseStringNames(file: File): Set<String> {
        if (!file.exists()) return emptySet()
        val doc: Document = newSecureFactory().newDocumentBuilder().parse(file)
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

    /**
     * Returns a [DocumentBuilderFactory] hardened against XXE injection by enabling
     * secure processing and disabling DOCTYPE declarations and external entity expansion.
     */
    private fun newSecureFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }

    /**
     * Locates the app/src/main/res directory by:
     * 1. Checking the `project.root` system property (set via Gradle test arguments in CI).
     * 2. Walking up from the current working directory until the relative path resolves.
     *
     * This makes the test resilient to varying working directories across IDE, Gradle, and CI.
     */
    private fun locateResDir(): File {
        val relPath = "app/src/main/res"

        // 1. Honour an explicit root override passed via -Dproject.root=<path>
        val sysProp = System.getProperty("project.root")
        if (sysProp != null) {
            val candidate = File(sysProp, relPath)
            if (candidate.exists()) return candidate
        }

        // 2. Walk up from the current working directory
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relPath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }

        error(
            "Cannot locate $relPath. Run tests from any ancestor of the project root, " +
                "or pass -Dproject.root=<path> as a JVM test argument."
        )
    }
}
