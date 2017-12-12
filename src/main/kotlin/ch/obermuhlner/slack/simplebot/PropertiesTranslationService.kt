package ch.obermuhlner.slack.simplebot

import java.util.*

interface PropertiesTranslationService : TranslationService {

    fun clear()

    fun parse(englishTranslations: Properties, germanTranslations: Properties)
}