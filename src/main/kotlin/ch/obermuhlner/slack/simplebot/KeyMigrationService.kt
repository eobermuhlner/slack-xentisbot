package ch.obermuhlner.slack.simplebot

import ch.obermuhlner.slack.simplebot.TranslationService.Translation

interface KeyMigrationService : TranslationService {

    fun parse(keyMigrationFile: String)

    fun getKeyNode(id: Int?): KeyNode?

    fun toMessage(keyNode: KeyNode): String

    data class KeyNode(
            val id: Int,
            val name: String?,
            val type: String?,
            val parent: Int?,
            val actionKey: Int?,
            val referenced: Int?,
            val children: MutableList<Int> = mutableListOf(),
            val mappings: MutableList<KeyMapping> = mutableListOf(),
            val translations: MutableList<KeyTranslation> = mutableListOf())

    data class KeyMapping(
            val id: Int,
            val refId: Int,
            val translations: MutableList<KeyTranslation> = mutableListOf())

    data class KeyTranslation(
            val language: String,
            val type: String?,
            val text: String)

}