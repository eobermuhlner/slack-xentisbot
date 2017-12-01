package ch.obermuhlner.slack.simplebot

import javax.xml.parsers.SAXParserFactory
import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.Attributes
import java.io.File

class XentisKeyMigration {
	private val idToKeyNode: MutableMap<Int, KeyNode> = mutableMapOf()
	
	fun parse(keyMigrationFile: String) {
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
						val keyNode = KeyNode(parseInt(attributes.getValue("id")), attributes.getValue("name"), attributes.getValue("standardComponentType"), parentId)
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
						val keyMapping = KeyMapping(parseInt(attributes.getValue("id")))
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
	
	fun getAllTranslations(): Set<Pair<String, String>> {
		val result: MutableSet<Pair<String, String>> = mutableSetOf()
		
		for (keyNode in idToKeyNode.values) {
			result.addAll(toGermanEnglishTranslations(keyNode.translations))
			
			for (keyMapping in keyNode.mappings) {
				result.addAll(toGermanEnglishTranslations(keyMapping.translations))
			}
		}
		
		return result
	}
	
	private fun toGermanEnglishTranslations(translations: Collection<KeyTranslation>): Set<Pair<String, String>> {
		val result: MutableSet<Pair<String, String>> = mutableSetOf()

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
				result.add(Pair(english, german))
			} 
		}
		
		return result
	}
	
	fun getKeyNode(id: Int): KeyNode? {
		return idToKeyNode[id]
	}
	
	data class KeyNode(
			val id: Int,
			val name: String?,
			val type: String?,
			val parent: Int?,
			val children: MutableList<Int> = mutableListOf(),
			val mappings: MutableList<KeyMapping> = mutableListOf(),
			val translations: MutableList<KeyTranslation> = mutableListOf()) {
		
		fun toMessage(): String {
			var message = "Key $id $name ${type.orEmpty()}\n"
			if (parent != null) {
				message += "    parent $parent\n"
			}
			message += "    children $children\n"
			
			for(translation in translations.sortedWith(compareBy(KeyTranslation::language, KeyTranslation::type))) {
				message += "    translation ${translation.language} ${translation.type.orEmpty()} : ${translation.text} \n"
			}

			for(keyMapping in mappings.sortedWith(compareBy(KeyMapping::id))) {
				message += "    keyMapping ${keyMapping.id}\n"
				for(translation in keyMapping.translations.sortedWith(compareBy(KeyTranslation::language, KeyTranslation::type))) {
					message += "        translation ${translation.language} ${translation.type.orEmpty()} : ${translation.text} \n"
				}
			}
								
			return message
		}
	}
	
	data class KeyMapping(
			val id: Int,
			val translations: MutableList<KeyTranslation> = mutableListOf())
	
	data class KeyTranslation(
			val language: String,
			val type: String?,
			val text: String)
}