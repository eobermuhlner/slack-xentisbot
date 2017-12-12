package ch.obermuhlner.slack.simplebot.xentis

import ch.obermuhlner.slack.simplebot.PropertiesTranslationService
import ch.obermuhlner.slack.simplebot.TranslationService.Translation
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Before
import java.util.*

class XentisPropertiesTranslationServiceTest {

    lateinit var service : PropertiesTranslationService

    @Before
    fun setup() {
        service = XentisPropertiesTranslationService()

        val englishProperties = Properties()
        val germanProperties = Properties()

        englishProperties.put("1", "one")
        germanProperties.put("1", "eins")
        englishProperties.put("2", "two")
        germanProperties.put("2", "zwei")

        service.parse(englishProperties, germanProperties)
    }

    @Test
    fun testTranslate() {
        assertEquals(2, service.translations.size)
        assertEquals(true, service.translations.contains(Translation("one", "eins")))
        assertEquals(true, service.translations.contains(Translation("two", "zwei")))
    }

    @Test
    fun testTranslate2() {
        assertEquals(2, service.translations.size)

        val englishProperties = Properties()
        val germanProperties = Properties()
        englishProperties.put("3", "three")
        germanProperties.put("3", "drei")
        service.parse(englishProperties, germanProperties)

        assertEquals(3, service.translations.size)
        assertEquals(true, service.translations.contains(Translation("one", "eins")))
        assertEquals(true, service.translations.contains(Translation("two", "zwei")))
        assertEquals(true, service.translations.contains(Translation("three", "drei")))
    }

    @Test
    fun testClear() {
        assertEquals(2, service.translations.size)

        service.clear()

        assertEquals(0, service.translations.size)
    }
}