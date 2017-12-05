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
import java.util.regex.Pattern

class SimpleBot {
	val properties = loadProperties("simplebot.properties")
	val apiKey = properties.getProperty("api.key");
	val session = connected(SlackSessionFactory.createWebSocketSlackSession(apiKey))
	val user = session.user()
	
	val observedChannelIds = HashSet<String>()

	val translations = mutableSetOf<Pair<String, String>>() 
	val xentisPropertiesTranslations = XentisPropertiesTranslations()
	val xentisDbSchema = XentisDbSchema()
	val xentisKeyMigration = XentisKeyMigration()
	val xentisSysCode = XentisSysCode()
	
	fun start () {
		val xentisSchemaFileName = properties.getProperty("xentis.schema")
		if (xentisSchemaFileName != null) {
			xentisDbSchema.parse(xentisSchemaFileName)
		}
		
		val xentisKeyMigrationFileName = properties.getProperty("xentis.keymigration")
		if (xentisKeyMigrationFileName != null) {
			xentisKeyMigration.parse(xentisKeyMigrationFileName)
			translations.addAll(xentisKeyMigration.translations)
		}

		val xentisSysCodeFileName = properties.getProperty("xentis.syscode")
		val xentisSysSubsetFileName = properties.getProperty("xentis.syssubset")
		if (xentisSysCodeFileName != null && xentisSysSubsetFileName != null) {
			xentisSysCode.parse(xentisSysCodeFileName, xentisSysSubsetFileName)
		}
		
		loadPropertiesTranslations()
		translations.addAll(xentisPropertiesTranslations.translations)
		
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
		
		println("Ready")
	}
	
	fun loadPropertiesTranslations() {
		var translationIndex = 0
		
		var file1: String?
		var file2: String?
		
		do {
			translationIndex++
			file1 = properties.getProperty("translation.${translationIndex}.source.properties")
			file2 = properties.getProperty("translation.${translationIndex}.target.properties")
	
			if (file1 != null && file2 != null) {
				xentisPropertiesTranslations.parse(file1, file2)
			}
		} while (file1 != null && file2 != null)
	}
	
	fun respondToMessage(event: SlackMessagePosted , messageContent: String) {
		println(messageContent)
		
		val args = messageContent.split(Pattern.compile("\\s+"))
		
		if (args.size == 0 || isCommand(args, "", 0) || isCommand(args, "help", 0)) {
			respondHelp(event)
			return
		}

		if (isCommand(args, "translate", 1)) {
			respondSearchTranslations(event, args[1])
			return
		}
		
		if (isCommand(args, "id", 1)) {
			val idText = args[1]
			val xentisId = parseXentisId(idText)
			if (xentisId != null) {
				respondAnalyzeXentisId(event, xentisId.first, xentisId.second)
			} else {
				session.sendMessage(event.channel, "This is a not a valid Xentis id: $idText. It must be 16 hex digits.")
			}
			return
		}

		if (isCommand(args, "syscode", 1)) {
			val syscodeText = args[1]
			val xentisId = parseXentisId(syscodeText)
			if (xentisId != null) {
				respondXentisSysCodeId(event, xentisId.first)
			} else {
				respondXentisSysCodeText(event, syscodeText)
			}
			return
		}

		if (isCommand(args, "classpart", 1)) {
			val classPartText = args[1]
			val xentisId = parseXentisId(classPartText, 4)
			if (xentisId != null) {
				respondAnalyzeXentisClassPart(event, xentisId.second)
			} else {
				session.sendMessage(event.channel, "This is a not a valid Xentis classpart: $classPartText. It must be 4 hex digits.")
			}
			return
		}

		if (isCommand(args, "tables", 1)) {
			respondXentisPartialTableName(event, args[1])
			return
		}
		
		if (isCommand(args, "table", 1)) {
			respondXentisTableName(event, args[1])
			return
		}
		
		if (isCommand(args, "key", 1)) {
			respondXentisKey(event, args[1])
			return
		}
		
		val xentisId = parseXentisId(messageContent)
		if (xentisId != null) {
			respondAnalyzeXentisId(event, xentisId.first, xentisId.second)
			
			respondXentisSysCodeId(event, xentisId.first, failMessage=false)
			return
		}
		
		val xentisClassPart = parseXentisId(messageContent, 4)
		if (xentisClassPart != null) {
			respondAnalyzeXentisClassPart(event, messageContent)
			return
		}
		
		respondXentisSysCodeText(event, messageContent, failMessage=false)
		respondXentisTableName(event, messageContent, failMessage=false)
		respondSearchTranslations(event, messageContent)
	}
	
	private fun isCommand(args: List<String>, command: String, argCount: Int): Boolean {
		return args.size >= (argCount + 1) && args[0] == command
	}
	
	private fun parseXentisId(text: String, length: Int = 16): Pair<Long, String>? {
		var hexText = text
		var id = text.toLongOrNull(16)

		if (id == null) {
			id = text.toLongOrNull(10)
			if (id == null) {
				return null
			}
			hexText = id.toString(16)
		}
		
		if (hexText.length != length) {
			return null
		} 
		
		return Pair(id, hexText)
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
				|$bot tables zuord
				|$bot table portfolio
				|$bot syscode 10510000940000aa
				|$bot syscode C_InstParam_PseudoVerfall
				|$bot key 1890
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
	
	private fun respondAnalyzeXentisId(event: SlackMessagePosted, id: Long, text: String) {
		session.sendMessage(event.channel, "This is a Xentis id: $text = decimal $id")
		
		respondAnalyzeXentisClassPart(event, text)
	}

	private fun respondXentisSysCodeId(event: SlackMessagePosted, id: Long, failMessage: Boolean = true) {
		val syscode = xentisSysCode.getSysCode(id)
		
		if (syscode == null) {
			if (failMessage) {
				session.sendMessage(event.channel, "This is not a valid a Xentis syscode: ${id.toString(16)}")
			}
			return
		}
		
		session.sendMessage(event.channel, syscode.toMessage())
	}
	
	private fun respondXentisSysCodeText(event: SlackMessagePosted, text: String, failMessage: Boolean = true) {
		val syscodeResults = xentisSysCode.findSysCodes(text)
		
		if (syscodeResults.size == 0) {
			if (failMessage) {
				session.sendMessage(event.channel, "No matching Xentis syscodes found.")
			}
			return
		}
		
		val syscodes = plural(syscodeResults.size, "syscode", "syscodes")
		var message = "_Found ${syscodeResults.size} $syscodes:_\n"
		
		for (syscode in syscodeResults) {
			message += syscode.toMessage()
			message += "\n"
		}
		
		session.sendMessage(event.channel, message)
	}
	
	private fun respondAnalyzeXentisClassPart(event: SlackMessagePosted, text: String) {
		val xentisClassPartText = text.substring(0, 4)
		val xentisClassPart = java.lang.Long.parseLong(xentisClassPartText, 16) and 0xfff
		val tableName = xentisDbSchema.getTableName(xentisClassPart)
		if (tableName != null) {
			session.sendMessage(event.channel, "The classpart $xentisClassPartText indicates a Xentis table $tableName")
		} else {
			session.sendMessage(event.channel, "This is not a Xentis classpart: $xentisClassPartText.")
		}
	}
	
	private fun respondXentisTableName(event: SlackMessagePosted, text: String, failMessage: Boolean = true) {
		val tableName = text.toUpperCase()
		
		val table = xentisDbSchema.getTable(tableName)
		if (table != null) {
			session.sendFile(event.channel, table.toMessage().toByteArray(), "TABLE_$tableName.txt")
		}
		
		val tableId = xentisDbSchema.getTableId(tableName)
		if (tableId != null) {
			val xentisClassPartText = (tableId or 0x1000).toString(16).padStart(4, '0')
			session.sendMessage(event.channel, "The classpart of the Xentis table $tableName is $xentisClassPartText")
		} else {
			if (failMessage) {
				session.sendMessage(event.channel, "This is not a Xentis table: $tableName.")
			}
		}
	}
	
	private fun respondXentisPartialTableName(event: SlackMessagePosted, text: String, failMessage: Boolean = true) {
		val tableNames = xentisDbSchema.getTableNames(text).sorted()
		
		if (tableNames.isEmpty()) {
			if (failMessage) {
				session.sendMessage(event.channel, "_No matching tables found._")
			}
			return
		}
		
		val tables = plural(tableNames.size, "table", "tables")
		var message = "_Found ${tableNames.size} matching $tables._\n"
		for(tableName in tableNames) {
			message += tableName + "\n"
		}
		
		session.sendMessage(event.channel, message)
	}

	private fun respondXentisKey(event: SlackMessagePosted, text: String, failMessage: Boolean = true) {
		val id = text.toIntOrNull()
		if (id == null) {
			if (failMessage) {
				session.sendMessage(event.channel, "Not a valid Xentis key id (must be an integer value): $text")
			}
			return
		}
		
		val keyNode = xentisKeyMigration.getKeyNode(id)
		if (keyNode == null) {
			if (failMessage) {
				session.sendMessage(event.channel, "No Xentis key node found for id: $id")
			}
			return
		}
		
		session.sendMessage(event.channel, xentisKeyMigration.toMessage(keyNode))
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
			for (result in limit(sortedList(perfectResults))) {
				message += result + "\n"
			}
		} else if (partialResults.size > 0) {
			val translations = plural(perfectResults.size, "translation", "translations")
			message = "_Found ${partialResults.size} $translations that partially matched this term:_\n"
			for (result in limit(sortedList(partialResults))) {
				message += result + "\n"
			}
		} else {
			message = "_No translations found._"
		}
		
		session.sendMessage(event.channel, message)
	}
	
	private fun sortedList(collection: Collection<String>): List<String> {
		val list: MutableList<String> = mutableListOf()
		list.addAll(collection)
		return list.sortedWith(compareBy({ it.length }, { it }))
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
