package ch.obermuhlner.slack.simplebot

import java.util.Properties
import java.io.FileReader
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener
import com.ullink.slack.simpleslackapi.SlackUser
import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy
import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.events.SlackEvent
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import java.io.BufferedReader

class SimpleBot {
	val properties = loadProperties("simplebot.properties")
	val apiKey = properties.getProperty("api.key");
	val session = connected(SlackSessionFactory.createWebSocketSlackSession(apiKey))
	val user = session.user()
	
	val observedChannelIds = HashSet<String>()

	val translations: MutableList<Pair<String, String>> = loadPropertiesTranslations()
	val xentisDbSchema = XentisDbSchema()
	
	fun start () {
		val xentisSchemaFileName = properties.getProperty("xentis.schema")
		if (xentisSchemaFileName != null) {
			xentisDbSchema.parse(xentisSchemaFileName)
		}
		
		val xentisKeyMigrationFileName = properties.getProperty("xentis.keymigration")
		if (xentisKeyMigrationFileName != null) {
			val xentisKeyMigration = XentisKeyMigration()
			xentisKeyMigration.parse(xentisKeyMigrationFileName)
			translations.addAll(xentisKeyMigration.translations)
		}
		
		session.addMessagePostedListener(SlackMessagePostedListener { event, _ ->
			if (event.sender.id != user.id) {
				val message = event.messageContent
				val directMessage = parseCommand(user.tag(), message)
				if (directMessage != null) {
					respondToMessage(event, directMessage) 
				} else if (event.channel.isDirect || observedChannelIds.contains(event.channel.id)) {
					respondToMessage(event, event.messageContent) 
				}
			}
		})
	}
	
	fun respondToMessage(event: SlackMessagePosted , messageContent: String) {
		println(messageContent)
		
		val help = parseCommand("help", messageContent)
		if (help != null || messageContent.trim() == "") {
			respondHelp(event)
			return
		}

		val translateText = parseCommand("translate", messageContent)
		if (translateText != null) {
			respondSearchTranslations(event, translateText)
			return
		}
		
		val idText = parseCommand("id", messageContent)
		if (idText != null) {
			val xentisId = parseXentisId(idText)
			if (xentisId != null) {
				respondAnalyzeXentisId(event, idText, xentisId)
			} else {
				session.sendMessage(event.channel, "This is a not a valid Xentis id: $idText. It must be 16 hex digits.")
			}
			return
		}

		val classPartText = parseCommand("classpart", messageContent)
		if (classPartText != null) {
			val xentisId = parseXentisId(classPartText, 4)
			if (xentisId != null) {
				respondAnalyzeXentisClassPart(event, classPartText)
			} else {
				session.sendMessage(event.channel, "This is a not a valid Xentis classpart: $classPartText. It must be 4 hex digits.")
			}
			return
		}

		val tableName = parseCommand("table", messageContent)
		if (tableName != null) {
			respondXentisTableName(event, tableName)
			return
		}
		
		val xentisId = parseXentisId(messageContent)
		if (xentisId != null) {
			respondAnalyzeXentisId(event, messageContent, xentisId)
			return
		}
		
		val xentisClassPart = parseXentisId(messageContent, 4)
		if (xentisClassPart != null) {
			respondAnalyzeXentisClassPart(event, messageContent)
			return
		}
		
		respondXentisTableName(event, messageContent, failMessage=false)
		respondSearchTranslations(event, messageContent)
	}
	
	private fun parseXentisId(text: String, length: Int = 16): Long? {
		if (text.length != length) {
			return null
		} 
		
		try {
			return java.lang.Long.parseLong(text, 16)
		} catch (ex: NumberFormatException) {
			return null
		}
	}
	
	private fun parseCommand(command: String, line: String): String? {
		if (line.startsWith(command)) {
			return line.substring(command.length).trim()
		}
		return null
	}
	
	private fun respondHelp(event: SlackMessagePosted) {
		val bot = "@" + user.userName
		session.sendMessage(event.channel, """
				|You can ask me questions by giving me a command with an appropriate argument.
				|Try it out by asking one of the following lines (just copy and paste into a new message):
				|$bot help
				|$bot id 108300000012be3c
				|$bot classpart 1083
				|$bot table portfolio
				|$bot translate interest
				|
				|If you talk with me without specifying a command, I will try to answer as best as I can (maybe giving multiple answers).
				|Please try one of the following:
				|$bot 108300000012be3c
				|$bot 1083
				|$bot portfolio
				|$bot interest
				|
				|If you talk with me in a direct chat you do not need to prefix the messages with my name $bot.
				|Please try one of the following:
				|108300000012be3c
				|1083
				|portfolio
				|interest
				""".trimMargin())
	}
	
	private fun respondAnalyzeXentisId(event: SlackMessagePosted, text: String, id: Long) {
		session.sendMessage(event.channel, "This is a Xentis id: $text = decimal $id")
		
		respondAnalyzeXentisClassPart(event, text)
	}
	
	private fun respondAnalyzeXentisClassPart(event: SlackMessagePosted, text: String) {
		val xentisClassPartText = text.substring(0, 4)
		val xentisClassPart = java.lang.Long.parseLong(xentisClassPartText, 16) and 0xff
		val tableName = xentisDbSchema.getTableName(xentisClassPart)
		if (tableName != null) {
			session.sendMessage(event.channel, "The classpart $xentisClassPartText indicates a Xentis table $tableName")
		} else {
			session.sendMessage(event.channel, "This is not a Xentis classpart: $xentisClassPartText.")
		}
	}
	
	private fun respondXentisTableName(event: SlackMessagePosted, text: String, failMessage: Boolean = true) {
		val tableId = xentisDbSchema.getTableId(text)
		if (tableId != null) {
			val xentisClassPartText = (tableId or 0x1000).toString(16).padStart(4, '0')
			session.sendMessage(event.channel, "The classpart of the Xentis table ${text.toUpperCase()} is $xentisClassPartText")
		} else {
			if (failMessage) {
				session.sendMessage(event.channel, "This is not a Xentis table: ${text.toUpperCase()}.")
			}
		}
	}
	
	private fun respondSearchTranslations(event: SlackMessagePosted, text: String) {
		if (text == "") {
			session.sendMessage(event.channel, "Nothing to translate.")
			return
		}
		
		val perfectResults = HashSet<String>()
		val partialResults = HashSet<String>()
		for((source, target) in translations) {
			if (source.equals(text, ignoreCase=true)) {
				perfectResults.add(target)
			}
			if (target.equals(text, ignoreCase=true)) {
				perfectResults.add(source)
			}
			if (source.contains(text, ignoreCase=true)) {
				partialResults.add(target)
			}
			if (target.contains(text, ignoreCase=true)) {
				partialResults.add(source)
			}
		}

		var message: String
		if (perfectResults.size > 0) {
			val translations = plural(perfectResults.size, "translation", "translations")
			message = "_Found ${perfectResults.size} $translations for exactly this term:_\n"
			for (result in limit(perfectResults)) {
				message += result + "\n"
			}
		} else if (partialResults.size > 0) {
			val translations = plural(perfectResults.size, "translation", "translations")
			message = "_Found ${partialResults.size} $translations that partially matched this term:_\n"
			for (result in limit(partialResults)) {
				message += result + "\n"
			}
		} else {
			message = "_No translations found._"
		}
		
		session.sendMessage(event.channel, message)
	}
	
	private fun limit(collection: Collection<String>, maxCount: Int = 10): Collection<String> {
		val result = ArrayList<String>()
		val n = Math.min(collection.size, maxCount)
		
		val iter = collection.iterator()
		for(i in 1..n) {
			result.add(iter.next())
		}
		
		if (n < collection.size) {
			result.add("...")
		}
		
		return result 
	} 

	private fun loadPropertiesTranslations(): MutableList<Pair<String, String>> {
		val result = ArrayList<Pair<String, String>>()
		
		var translationIndex = 0
		
		var file1: String?
		var file2: String?
		
		do {
			translationIndex++
			file1 = properties.getProperty("translation.${translationIndex}.source.properties")
			file2 = properties.getProperty("translation.${translationIndex}.target.properties")
	
			if (file1 != null && file2 != null) {
				val translations1 = loadProperties(file1)
				val translations2 = loadProperties(file2)
				
				for(key in translations1.keys) {
					if(key is String) {
						val translation1 = translations1.getProperty(key)
						val translation2 = translations2.getProperty(key)
						if (translation1 != null && translation2 != null) {
							result.add(Pair(translation1, translation2))
						} 
					}
				}
			}
		} while (file1 != null && file2 != null)
			
		return result
	}
	
	private fun connected(s: SlackSession): SlackSession {
		s.connect()
		return s
	}
}

fun loadProperties(name: String): Properties {
	val properties = Properties()

    //val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("name")
	BufferedReader(FileReader(name)).use {
		properties.load(it)
	}

	return properties
}

fun plural(count: Int, singular: String, plural: String): String {
	if (count == 1) {
		return singular
	} else {
		return plural
	}
}

fun SlackUser.tag() = "<@" + this.id + ">" 

fun SlackSession.user(): SlackUser {
	val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()

	val params = HashMap<String, String>()
	val replyHandle = this.postGenericSlackCommand(params, "auth.test")
	val reply = replyHandle.reply.plainAnswer
	
	val response = gson.fromJson(reply, AuthTestResponse::class.java)
	if (!response.ok) {
		throw SlackException(response.error)
	}
	
	return this.findUserById(response.userId)
}

private class AuthTestResponse {
	var ok: Boolean = false
	var error: String = ""
	var warning: String = ""
	var userId: String = ""
	var user: String = ""
	var teamId: String = ""
	var team: String = ""
}

fun main(args: Array<String>) {
	val bot = SimpleBot()
	bot.start()
}
