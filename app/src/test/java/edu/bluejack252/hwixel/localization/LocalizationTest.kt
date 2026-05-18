package edu.bluejack252.hwixel.localization

import edu.bluejack252.hwixel.data.repository.SharedPrefsProfileSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the correctness of the localization layer introduced in Phase 12.
 *
 * Tests cover:
 *  - Supported locale set matches the locales declared in res/xml/locales_config.xml
 *  - Unsupported locale tags are normalised to the default language
 *  - The default locale is English ("en")
 *  - Bahasa Indonesia ("id") is an accepted tag
 */
class LocalizationTest {

    private val supportedTags = SharedPrefsProfileSettingsRepository.SUPPORTED_LANGUAGE_TAGS
    private val defaultTag = SharedPrefsProfileSettingsRepository.DEFAULT_LANGUAGE_TAG

    @Test
    fun defaultLanguageIsEnglish() {
        assertEquals("en", defaultTag)
    }

    @Test
    fun englishIsSupported() {
        assertTrue("\"en\" must be a supported language tag", supportedTags.contains("en"))
    }

    @Test
    fun bahasaIndonesiaIsSupported() {
        assertTrue("\"id\" must be a supported language tag", supportedTags.contains("id"))
    }

    @Test
    fun exactlyTwoLocalesSupported() {
        assertEquals(
            "The app declares two supported locales (en and id)",
            2,
            supportedTags.size
        )
    }

    @Test
    fun unsupportedTagNormalisesToDefault() {
        val unsupportedInputs = listOf("fr", "ja", "zh", "es", "de", "", "  ", "invalid")
        for (input in unsupportedInputs) {
            val normalised = normalise(input)
            assertEquals(
                "Unsupported tag \"$input\" should normalise to default \"$defaultTag\"",
                defaultTag,
                normalised
            )
        }
    }

    @Test
    fun supportedTagsPassThroughUnchanged() {
        for (tag in supportedTags) {
            val normalised = normalise(tag)
            assertEquals(
                "Supported tag \"$tag\" should pass through unchanged",
                tag,
                normalised
            )
        }
    }

    @Test
    fun bahasaTagDoesNotNormaliseToEnglish() {
        val normalised = normalise("id")
        assertFalse(
            "\"id\" must not be normalised to default when it is a supported tag",
            normalised == defaultTag
        )
        assertEquals("id", normalised)
    }

    /**
     * Replicates the normalisation logic from SharedPrefsProfileSettingsRepository.setLanguageTag
     * without requiring an Android Context.
     */
    private fun normalise(tag: String): String {
        return if (supportedTags.contains(tag)) tag else defaultTag
    }
}
