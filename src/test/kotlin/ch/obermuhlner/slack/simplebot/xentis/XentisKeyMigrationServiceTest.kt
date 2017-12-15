package ch.obermuhlner.slack.simplebot.xentis

import ch.obermuhlner.slack.simplebot.KeyMigrationService
import ch.obermuhlner.slack.simplebot.TranslationService
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.StringReader

class XentisKeyMigrationServiceTest {

    private lateinit var service: XentisKeyMigrationService

    @Before
    fun setup() {
        service = XentisKeyMigrationService()
        service.parse(StringReader("""
            |<?xml version="1.0" encoding="UTF-8"?>
            |<com.profidatagroup.util.keymigration.model:KeyNode xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:com.profidatagroup.util.keymigration.model="http:///com/profidatagroup/util/keymigration/model.ecore">
            |  <children xsi:type="com.profidatagroup.util.keymigration.model:StandardComponentKeyNode" id="2" standardComponentType="MASK"/>
            |  <children xsi:type="com.profidatagroup.util.keymigration.model:StandardComponentKeyNode" id="5" standardComponentType="BUTTON_LOESCHEN">
            |    <translations text="-" languageCode="DE"/>
            |    <translations text="Löschen" languageCode="DE" translationType="TOOLTIP_TRANSLATION"/>
            |    <translations text="Delete" languageCode="EN" translationType="TOOLTIP_TRANSLATION"/>
            |    <translations text="-" languageCode="EN"/>
            |  </children>
            |  <children id="49107" name="GUI-Komponenten">
            |    <children xsi:type="com.profidatagroup.util.keymigration.model:ReferencingKeyNode" id="1828" name="Component Notizen" referencedNode="2">
            |      <children xsi:type="com.profidatagroup.util.keymigration.model:ReferencingKeyNode" id="1830" name="Notiz löschen" referencedNode="5">
            |        <keyMapping refId="5" id="1830">
            |          <translations text="Notiz löschen" languageCode="DE" translationType="MODEL_TRANSLATION"/>
            |          <translations text="Delete Notice" languageCode="EN" translationType="MODEL_TRANSLATION"/>
            |        </keyMapping>
            |      </children>
            |    </children>
            |  </children>
            |</com.profidatagroup.util.keymigration.model:KeyNode>
            """.trimMargin()))
    }

    @Test
    fun test_getKeyNode_id_null() {
        val keyNode = service.getKeyNode(null)
        assertEquals(null, keyNode)
    }

    @Test
    fun test_getKeyNode_id_unknown() {
        val keyNode = service.getKeyNode(9999)
        assertEquals(null, keyNode)
    }

    @Test
    fun test_getKeyNode_id_2() {
        val keyNode = service.getKeyNode(2)!!
        assertEquals(
                KeyMigrationService.KeyNode(2,
                        null,
                        "MASK",
                        null,
                        0,
                        0),
                keyNode)
    }

    @Test
    fun test_getKeyNode_id_5() {
        val keyNode = service.getKeyNode(5)!!
        assertEquals(
                KeyMigrationService.KeyNode(5,
                        null,
                        "BUTTON_LOESCHEN",
                        null,
                        0,
                        0,
                        translations = mutableSetOf(
                                KeyMigrationService.KeyTranslation("DE", null, "-"),
                                KeyMigrationService.KeyTranslation("EN", null, "-"),
                                KeyMigrationService.KeyTranslation("DE", "TOOLTIP_TRANSLATION", "Löschen"),
                                KeyMigrationService.KeyTranslation("EN", "TOOLTIP_TRANSLATION", "Delete"))),
                keyNode)
    }

    @Test
    fun test_getKeyNode_id_1828() {
        val keyNode = service.getKeyNode(1828)!!
        assertEquals(
                KeyMigrationService.KeyNode(1828,
                        "Component Notizen",
                        null,
                        49107,
                        0,
                        2,
                        children = mutableSetOf(1830)),
                keyNode)
    }

    @Test
    fun test_getKeyNode_id_1830() {
        val keyNode = service.getKeyNode(1830)!!
        assertEquals(
                KeyMigrationService.KeyNode(1830,
                        "Notiz löschen",
                        null,
                        1828,
                        0,
                        5,
                        mappings = mutableSetOf(
                                KeyMigrationService.KeyMapping(1830, 5, translations = mutableSetOf(
                                        KeyMigrationService.KeyTranslation("DE", "MODEL_TRANSLATION", "Notiz löschen"),
                                        KeyMigrationService.KeyTranslation("EN", "MODEL_TRANSLATION", "Delete Notice")
                                ))
                        )),
                keyNode)
    }

    @Test
    fun test_translation() {
        Assert.assertEquals(
                mutableSetOf(
                        TranslationService.Translation("-", "-"),
                        TranslationService.Translation("Delete", "Löschen"),
                        TranslationService.Translation("Delete Notice", "Notiz löschen")),
                service.translations)
    }

    @Test
    fun test_toMessage_id_2() {
        val keyNode = service.getKeyNode(2)!!
        val actual = service.toMessage(keyNode)
        val expected = """
            |Key 2  type _MASK_
            |    children []
            |
            """.trimMargin()
        assertEquals(expected, actual)
    }

    @Test
    fun test_toMessage_id_5() {
        val keyNode = service.getKeyNode(5)!!
        val actual = service.toMessage(keyNode)
        val expected = """
                |Key 5  type _BUTTON_LOESCHEN_
                |    children []
                |    translation DE  : _-_
                |    translation DE TOOLTIP_TRANSLATION : _Löschen_
                |    translation EN  : _-_
                |    translation EN TOOLTIP_TRANSLATION : _Delete_
                |
            """.trimMargin()
        assertEquals(expected, actual)
    }

    @Test
    fun test_toMessage_id_1830() {
        val keyNode = service.getKeyNode(1830)!!
        val actual = service.toMessage(keyNode)
        val expected = """
                |Key 1830 _Notiz löschen_ is a  BUTTON_LOESCHEN
                |    parent 1828
                |    children []
                |    keyMapping 1830 _Notiz löschen_ references 5  is a  BUTTON_LOESCHEN
                |        translation DE MODEL_TRANSLATION : _Notiz löschen_
                |        translation EN MODEL_TRANSLATION : _Delete Notice_
                |
            """.trimMargin()
        assertEquals(expected, actual)
    }
}
