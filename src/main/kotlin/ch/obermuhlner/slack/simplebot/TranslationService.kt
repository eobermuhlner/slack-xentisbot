package ch.obermuhlner.slack.simplebot

interface TranslationService {

    val translations: Set<Translation> get

    data class Translation(
            val english: String,
            val german: String)

}