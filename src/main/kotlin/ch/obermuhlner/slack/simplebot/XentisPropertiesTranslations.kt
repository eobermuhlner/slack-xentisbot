package ch.obermuhlner.slack.simplebot

class XentisPropertiesTranslations {
	private val _translations = mutableSetOf<XentisTranslation>()
	
	val translations get() = _translations
	
	fun parse(sourceFile: String, targetFile: String) {
		_translations.clear()
		
		val translations1 = loadProperties(sourceFile)
		val translations2 = loadProperties(targetFile)
		
		for(key in translations1.keys) {
			if(key is String) {
				val translation1 = translations1.getProperty(key)
				val translation2 = translations2.getProperty(key)
				if (translation1 != null && translation2 != null) {
					_translations.add(XentisTranslation(translation1, translation2))
				} 
			}
		}
	}
}