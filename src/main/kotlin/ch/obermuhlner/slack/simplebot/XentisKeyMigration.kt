package ch.obermuhlner.slack.simplebot

import javax.xml.parsers.SAXParserFactory
import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.Attributes
import java.io.File

class XentisKeyMigration {
	val translations = ArrayList<Pair<String, String>>()
	
	fun parse(keyMigrationFile: String) {
		val factory = SAXParserFactory.newInstance()
		val parser = factory.newSAXParser()
		
		val handler = object: DefaultHandler() {
			var translationKey = ""
			val currentTranslation = HashMap<String, String>()
			
			override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
				when (qName) {
					"children" -> {
						translationKey = attributes.getValue("id")
						currentTranslation.clear()
					}
					"translations" -> {
						val languageCode = attributes.getValue("languageCode")
						val text = attributes.getValue("text")
						val translationType = attributes.getValue("translationType")
						if (translationType == null) {
							currentTranslation[languageCode] = text
						}
					}
				}
			}
			
			override fun endElement(uri: String, localName: String, qName: String) {
				when (qName) {
					"children" -> {
						val german = currentTranslation["DE"]
						val english = currentTranslation["EN"]
						if (german != null && english != null) {
							translations.add(Pair(german, english))
						}
					}
				}
			}
		}
		
		parser.parse(File(keyMigrationFile), handler)
	}
}