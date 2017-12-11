package ch.obermuhlner.slack.simplebot

interface PropertiesTranslationService : TranslationService {

    fun clear()

    fun parse(sourceFile: String, targetFile: String)
}