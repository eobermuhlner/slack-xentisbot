package ch.obermuhlner.slack.simplebot

import java.io.Reader

interface KeyMigrationService : TranslationService {

    fun parse(keyMigrationReader: Reader)

    fun getKeyNode(id: Int?): KeyNode?

    fun toMessage(keyNode: KeyNode): String

    data class KeyNode(
            val id: Int,
            val name: String?,
            val type: String?,
            val parent: Int?,
            val actionKey: Int?,
            val referenced: Int?,
            val children: MutableSet<Int> = mutableSetOf(),
            val mappings: MutableSet<KeyMapping> = mutableSetOf(),
            val translations: MutableSet<KeyTranslation> = mutableSetOf())

    data class KeyMapping(
            val id: Int,
            val refId: Int,
            val translations: MutableSet<KeyTranslation> = mutableSetOf())

    data class KeyTranslation(
            val language: String,
            val type: String?,
            val text: String)

}