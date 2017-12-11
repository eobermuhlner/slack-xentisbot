package ch.obermuhlner.slack.simplebot.xentis

import ch.obermuhlner.slack.simplebot.KeyMigrationService
import ch.obermuhlner.slack.simplebot.KeyMigrationService.KeyNode
import ch.obermuhlner.slack.simplebot.KeyMigrationService.KeyMapping
import ch.obermuhlner.slack.simplebot.KeyMigrationService.KeyTranslation
import ch.obermuhlner.slack.simplebot.TranslationService.Translation
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.Attributes
import java.io.File

class XentisKeyMigrationService : KeyMigrationService {
	private val idToKeyNode: MutableMap<Int, KeyNode> = mutableMapOf()
	
	override val translations get() = getAllTranslations()
	
	override fun parse(keyMigrationFile: String) {
		idToKeyNode.clear()
		
		val factory = SAXParserFactory.newInstance()
		val parser = factory.newSAXParser()
		
		val handler = object: DefaultHandler() {
			val keyNodeStack: MutableList<KeyNode> = mutableListOf()
			var currentKeyMapping: KeyMapping? = null
			
			var translationKey = ""
			val currentTranslation = HashMap<String, String>()
			
			override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
				when (qName) {
					"children" -> {
						val parent: KeyNode? = keyNodeStack.lastOrNull()
						val parentId: Int? = parent?.id
						val keyNode = KeyNode(
                                id = parseInt(attributes.getValue("id")),
                                name = attributes.getValue("name"),
                                type = attributes.getValue("standardComponentType"),
                                parent = parentId,
                                actionKey = parseInt(attributes.getValue("actionKeyNode")),
                                referenced = parseInt(attributes.getValue("referencedNode")))
						idToKeyNode[keyNode.id] = keyNode
						keyNodeStack.add(keyNode)
						if (parent != null) {
							parent.children.add(keyNode.id)
						}
						
						translationKey = attributes.getValue("id")
						currentTranslation.clear()
					}
					"translations" -> {
						val translation = KeyTranslation(attributes.getValue("languageCode"), attributes.getValue("translationType"), attributes.getValue("text"))
						val keyMapping = currentKeyMapping
						if (keyMapping != null) {
							keyMapping.translations.add(translation)
						} else {
							val keyNode: KeyNode = keyNodeStack.last()
							keyNode.translations.add(translation)
						}
						
						if (translation.type == null) {
							currentTranslation[translation.language] = translation.text
						}
					}
					"keyMapping" -> {
						val keyMapping = KeyMapping(
                                id = parseInt(attributes.getValue("id")),
                                refId = parseInt(attributes.getValue("refId")))
						val keyNode: KeyNode = keyNodeStack.last()
						keyNode.mappings.add(keyMapping)
						currentKeyMapping = keyMapping
					}
				}
			}
			
			override fun endElement(uri: String, localName: String, qName: String) {
				when (qName) {
					"children" -> {
						keyNodeStack.removeAt(keyNodeStack.lastIndex)
					}
					"keyMapping" -> {
						currentKeyMapping = null
					}
				}
			}
		}
		
		parser.parse(File(keyMigrationFile), handler)
	}
	
	private fun parseInt(text: String?, defaultValue: Int = 0): Int {
		if (text == null) {
			return defaultValue
		}
		return java.lang.Integer.parseInt(text)
	}
	
	private fun getAllTranslations(): Set<Translation> {
		val result: MutableSet<Translation> = mutableSetOf()
		
		for (keyNode in idToKeyNode.values) {
			result.addAll(toEnglishGermanTranslations(keyNode.translations))
			
			for (keyMapping in keyNode.mappings) {
				result.addAll(toEnglishGermanTranslations(keyMapping.translations))
			}
		}
		
		return result
	}
	
	private fun toEnglishGermanTranslations(translations: Collection<KeyTranslation>): Set<Translation> {
		val result: MutableSet<Translation> = mutableSetOf()

		val translationMap: MutableMap<Pair<String, String?>, String> = mutableMapOf()
		val translationTypes: MutableSet<String?> = mutableSetOf()
		for (translation in translations) {
			translationMap[Pair(translation.language, translation.type)] = translation.text
			translationTypes.add(translation.type)
		}
		
		for (translationType in translationTypes) {
			val english = translationMap[Pair("EN", translationType)] 
			val german = translationMap[Pair("DE", translationType)]
			if (english != null && german != null) {
				result.add(Translation(english, german))
			} 
		}
		
		return result
	}
	
	override fun getKeyNode(id: Int?): KeyNode? {
		if (id == null) {
			return null
		}
		return idToKeyNode[id]
	}

	override fun toMessage(keyNode: KeyNode): String {
		val referencedKeyNode = getKeyNode(keyNode.referenced)
		var message = "Key ${keyNode.id} ${italic(keyNode.name)}"
		message += prefix(" type", italic(keyNode.type))
		message += prefix(" action ", keyNode.actionKey)
		message += prefix(" is a ", referencedKeyNode?.type)
		message += "\n"
		
		if (keyNode.parent != null) {
			message += "    parent ${keyNode.parent}\n"
		}
		message += "    children ${keyNode.children}\n"
		
		for(translation in keyNode.translations.sortedWith(compareBy(KeyTranslation::language, KeyTranslation::type))) {
			message += "    translation ${translation.language} ${translation.type.orEmpty()} : ${translation.text} \n"
		}

		for(mapping in keyNode.mappings.sortedWith(compareBy(KeyMapping::id))) {
			message += "    keyMapping ${mapping.id}"
			val mappingKeyNode = getKeyNode(mapping.id)
			if (mappingKeyNode != null) {
				message += " "
				message += italic(mappingKeyNode.name)
			}
			val mappingRefKeyNode = getKeyNode(mapping.refId)
			if (mappingRefKeyNode != null) {
				message += " references "
				message += mappingRefKeyNode.id
				message += " "
				message += italic(mappingRefKeyNode.name)
				message += prefix(" is a ", mappingRefKeyNode.type)
			}
			message += "\n"
			for(translation in mapping.translations.sortedWith(compareBy(KeyTranslation::language, KeyTranslation::type))) {
				message += "        translation ${translation.language} ${translation.type.orEmpty()} : _${translation.text}_\n"
			}
		}

		return message
	}
	
	private fun prefix(prefix: String, value : Any?): String {
		if (value == null || value == "" || value == 0) {
			return ""
		} else {
			return "${prefix} ${value}"
		}
	}

	private fun italic(value : Any?): String {
		if (value == null || value == "" || value == 0) {
			return ""
		} else {
			return "_${value}_"
		}
	}
}