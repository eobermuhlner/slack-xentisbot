package ch.obermuhlner.slack.simplebot.xentis

import ch.obermuhlner.slack.simplebot.PropertiesTranslationService
import ch.obermuhlner.slack.simplebot.TranslationService.Translation
import ch.obermuhlner.slack.simplebot.loadProperties

class XentisPropertiesTranslationService : PropertiesTranslationService {

	private val _translations = mutableSetOf<Translation>()
	
	override val translations get() = _translations

	override fun clear() {
		_translations.clear()
	}

	override fun parse(sourceFile: String, targetFile: String) {
		val translations1 = loadProperties(sourceFile)
		val translations2 = loadProperties(targetFile)
		
		for(key in translations1.keys) {
			if(key is String) {
				val translation1 = translations1.getProperty(key)
				val translation2 = translations2.getProperty(key)
				if (translation1 != null && translation2 != null) {
					_translations.add(Translation(translation1, translation2))
				} 
			}
		}
	}
}