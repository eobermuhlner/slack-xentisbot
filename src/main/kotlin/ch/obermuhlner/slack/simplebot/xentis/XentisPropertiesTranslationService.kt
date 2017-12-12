package ch.obermuhlner.slack.simplebot.xentis

import ch.obermuhlner.slack.simplebot.PropertiesTranslationService
import ch.obermuhlner.slack.simplebot.TranslationService.Translation
import java.util.*

class XentisPropertiesTranslationService : PropertiesTranslationService {

	private val _translations = mutableSetOf<Translation>()
	
	override val translations get() = _translations

	override fun clear() {
		_translations.clear()
	}

	override fun parse(englishTranslations: Properties, germanTranslations: Properties) {
		for(key in englishTranslations.keys) {
			if(key is String) {
				val english = englishTranslations.getProperty(key)
				val german = germanTranslations.getProperty(key)
				if (english != null && german != null) {
					_translations.add(Translation(english, german))
				} 
			}
		}
	}
}