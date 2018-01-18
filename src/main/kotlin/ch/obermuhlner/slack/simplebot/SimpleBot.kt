package ch.obermuhlner.slack.simplebot

import ch.obermuhlner.slack.simplebot.TranslationService.Translation
import ch.obermuhlner.slack.simplebot.xentis.*
import java.util.Properties
import java.io.FileReader
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import com.ullink.slack.simpleslackapi.SlackUser
import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy
import com.ullink.slack.simpleslackapi.SlackAttachment
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import java.io.BufferedReader
import java.util.regex.Pattern
import java.io.PrintWriter
import java.io.StringWriter

class SimpleBot(
		private val sysCodeService: SysCodeService = XentisSysCodeService(),
		private val propertiesTranslations: PropertiesTranslationService = XentisPropertiesTranslationService(),
		private val dbSchemaService: DbSchemaService = XentisDbSchemaService(),
		private val keyMigrationService: KeyMigrationService = XentisKeyMigrationService()) {

	private lateinit var session: SlackSession
	private lateinit var user: SlackUser
	private var adminUser: SlackUser? = null

	private val observedChannelIds = HashSet<String>()

	private val translations = mutableSetOf<Translation>()

	private val commandHandlers: List<CommandHandler> = listOf(
			CommandHandler("help") { event, _, heuristic ->
				if (!heuristic) {
					respondHelp(event)
					true
				} else {
					false
				}
			}, CommandHandler("refresh") { event, _, heuristic ->
				if (!heuristic) {
					respond(event, "Refreshing information about Xentis...")
					loadData()
					respondStatus(event)
					true
				} else {
					false
				}
			}, CommandHandler("status") { event, _, heuristic ->
				if (!heuristic) {
					respondStatus(event)
					true
				} else {
					false
				}
			}, CommandHandler("statistics") { event, _, heuristic ->
				if (!heuristic) {
					respondStatistics(event)
					true
				} else {
					false
				}
			}, SimpleCommandHandler("id") { event, arg, heuristic ->
				val xentisId = parseXentisId(arg)
				if (xentisId != null) {
					respondAnalyzeXentisId(event, xentisId.first, xentisId.second, failMessage = !heuristic)
				} else {
					if (!heuristic) {
						respond(event, "`$arg` is a not a valid Xentis id. It must be 16 hex digits.")
					}
					false
				}
			}, SimpleCommandHandler("syscodes") { event, arg, heuristic ->
				if (!heuristic) {
					respondXentisPartialSysCodeText(event, arg, failMessage = !heuristic)
				} else {
					false
				}
			}, SimpleCommandHandler("syscode") { event, arg, heuristic ->
				val xentisId = parseXentisId(arg)
				if (!heuristic || xentisId != null || arg.startsWith("C_")) {
					if (xentisId != null) {
						respondXentisSysCodeId(event, xentisId.first, failMessage = !heuristic)
					} else {
						respondXentisSysCodeText(event, arg, failMessage = !heuristic)
					}
				} else {
					false
				}
			}, SimpleCommandHandler("classpart") { event, arg, heuristic ->
				val xentisId = parseXentisId(arg, 4)
				if (xentisId != null) {
					respondAnalyzeXentisClassPart(event, xentisId.second, failMessage = !heuristic)
				} else {
					if (!heuristic) {
						respond(event, "`$arg` is a not a valid Xentis classpart. It must be 4 hex digits.")
					}
					false
				}
			}, SimpleCommandHandler("tables") { event, arg, heuristic ->
				if (!heuristic || arg.isUpperCase()) {
					respondXentisPartialTableName(event, arg, failMessage = !heuristic)
				} else {
					false
				}
			}, SimpleCommandHandler("table") { event, arg, heuristic ->
				if (!heuristic || arg.isUpperCase()) {
					respondXentisTableName(event, arg, failMessage = !heuristic)
				} else {
					false
				}
			}, SimpleCommandHandler("key") { event, arg, heuristic ->
				respondXentisKey(event, arg, failMessage = !heuristic)
			}, SimpleCommandHandler("dec") { event, arg, heuristic ->
				if (!heuristic) {
					respondNumberConversion(event, arg.removeSuffix("L"), 10, introMessage = false)
				} else {
					false
				}
			}, SimpleCommandHandler("hex") { event, arg, heuristic ->
				if (!heuristic) {
					if (arg.startsWith("-0x")) {
						respondNumberConversion(event, "-" + arg.removePrefix("-0x").removeSuffix("L"), 16, introMessage = false)
					} else {
						respondNumberConversion(event, arg.removePrefix("0x").removeSuffix("L"), 16, introMessage = false)
					}
				} else {
					false
				}
			}, SimpleCommandHandler("bin") { event, arg, heuristic ->
				if (!heuristic) {
					if (arg.startsWith("-0b")) {
						respondNumberConversion(event, "-" + arg.removePrefix("-0b").removeSuffix("L"), 2, introMessage = false)
					} else {
						respondNumberConversion(event, arg.removePrefix("0b").removeSuffix("L"), 2, introMessage = false)
					}
				} else {
					false
				}
			}, SimpleCommandHandler("number") { event, arg, heuristic ->
				if (arg.startsWith("0x")) {
					respondNumberConversion(event, arg.removePrefix("0x").removeSuffix("L"), 16, failMessage = !heuristic, introMessage = false)
				} else if (arg.startsWith("-0x")) {
					respondNumberConversion(event, "-" + arg.removePrefix("-0x").removeSuffix("L"), 16, failMessage = !heuristic, introMessage = false)
				} else if (arg.startsWith("0b")) {
					respondNumberConversion(event, arg.removePrefix("0b").removeSuffix("L"), 2, failMessage = !heuristic, introMessage = false)
				} else if (arg.startsWith("-0b")) {
					respondNumberConversion(event, "-" + arg.removePrefix("-0b").removeSuffix("L"), 2, failMessage = !heuristic, introMessage = false)
				} else {
					var result = false
					result = result or respondNumberConversion(event, arg.removeSuffix("L"), 10, failMessage = !heuristic)
					result = result or respondNumberConversion(event, arg.removeSuffix("L"), 16, failMessage = !heuristic)
					result = result or respondNumberConversion(event, arg.removeSuffix("L"), 2, failMessage = !heuristic)
					result
				}
            }, SimpleCommandHandler("pd") { event, arg, heuristic ->
                if (!heuristic) {
                    respond(event, "Profidata $arg", imageUrl = "http://pdintra/php/mafotos_virt/$arg.jpg")
					true
                } else {
					false
				}
            }, SimpleCommandHandler("image") { event, arg, heuristic ->
                if (!heuristic) {
                    respond(event, "Image $arg", imageUrl = arg)
					true
                } else {
					false
				}
            }, SimpleCommandHandler("translate") { event, arg, _ ->
               respondSearchTranslations(event, arg)
			}
		)

	private val explicitCommandCount : MutableMap<String, Int> = mutableMapOf()
	private val heuristicCommandCount : MutableMap<String, Int> = mutableMapOf()

	fun start () {
		loadData()

		session.addMessagePostedListener({ event, _ ->
			handleMessagePosted(event)
		})

		println("Ready")
	}

	private fun loadData() {
		val properties = loadProperties("simplebot.properties")

		val apiKey = properties.getProperty("api.key")
		session = connected(SlackSessionFactory.createWebSocketSlackSession(apiKey))

		user = session.user()
		adminUser = findUser(properties.getProperty("admin.user"))

		val xentisSchemaFileName = properties.getProperty("xentis.schema")
		if (xentisSchemaFileName != null) {
			dbSchemaService.parse(FileReader(xentisSchemaFileName))
		}

		val xentisKeyMigrationFileName = properties.getProperty("xentis.keymigration")
		if (xentisKeyMigrationFileName != null) {
			keyMigrationService.parse(FileReader(xentisKeyMigrationFileName))
			translations.addAll(keyMigrationService.translations)
		}

		val xentisSysCodeFileName = properties.getProperty("xentis.syscode")
		val xentisSysSubsetFileName = properties.getProperty("xentis.syssubset")
		if (xentisSysCodeFileName != null && xentisSysSubsetFileName != null) {
			sysCodeService.parseSysCodes(FileReader(xentisSysCodeFileName))
            sysCodeService.parseSysSubsets(FileReader(xentisSysSubsetFileName))
		}
        sysCodeService.parseDbSchema(dbSchemaService)

		loadPropertiesTranslations(properties)
		translations.addAll(propertiesTranslations.translations)
		translations.addAll(sysCodeService.translations)
	}

	private fun findUser(user: String?): SlackUser? {
		if (user == null) {
			return null
		}

		val userById = session.findUserById(user)
		if (userById != null) {
			return userById
		}

		return session.findUserByUserName(user)
	}

	private fun handleMessagePosted(event: SlackMessagePosted) {
		try {
			if (event.sender.id != user.id) {
				val message = event.messageContent
				val directMessage = parseCommand(user.tag(), message)
				if (directMessage != null) {
					respondToMessage(event, directMessage)
				} else if (event.channel.isDirect || observedChannelIds.contains(event.channel.id)) {
					respondToMessage(event, event.messageContent)
				}
			}
		} catch (ex: Exception) {
			handleException("""
					|*Failed to handle message:*
					|from: ${event.sender.realName}
					|channel: ${event.channel.name}
					|content: ${event.messageContent}
					""".trimMargin(), ex)
		}
	}

	private fun handleException(message: String, ex: Exception) {
		ex.printStackTrace()

		if (adminUser != null) {
			val stringWriter = StringWriter()
			ex.printStackTrace(PrintWriter(stringWriter))

			session.sendMessageToUser(adminUser, message, null)
			session.sendFileToUser(adminUser, stringWriter.toString().toByteArray(), "Stacktrace.txt")
		}
	}

	private fun loadPropertiesTranslations(properties: Properties) {
		propertiesTranslations.clear()

		var translationIndex = 0

		var success: Boolean
		do {
			translationIndex++
			val file1 = properties.getProperty("translation.${translationIndex}.source.properties")
			val file2 = properties.getProperty("translation.${translationIndex}.target.properties")

			if (file1 != null && file2 != null) {
				propertiesTranslations.parse(loadProperties(file1), loadProperties(file2))
				success = true
			} else {
				success = false
			}
		} while (success)
	}

	private fun respondToMessage(event: SlackMessagePosted , messageContent: String) {
		println(messageContent)

		val args = messageContent.split(Pattern.compile("\\s+"))

		for (commandHandler in commandHandlers) {
			println("Attempt explicit execution of ${commandHandler.name}")
			val done = commandHandler.execute(event, args)
			if (done) {
				println("Success explicit execution of ${commandHandler.name}")
				incrementCommandCount(commandHandler, false)
				return
			}
		}

		for (commandHandler in commandHandlers) {
			println("Attempt heuristic execution of ${commandHandler.name}")
			val done = commandHandler.execute(event, args, true)
			if (done) {
				println("Success heuristic execution of ${commandHandler.name}")
				incrementCommandCount(commandHandler, true)
			}
		}
	}

	private fun incrementCommandCount(commandHandler: CommandHandler, heuristic: Boolean) {
		if (heuristic) {
			heuristicCommandCount[commandHandler.name] = heuristicCommandCount.computeIfAbsent(commandHandler.name, { 0 }) + 1
		} else {
			explicitCommandCount[commandHandler.name] = explicitCommandCount.computeIfAbsent(commandHandler.name, { 0 }) + 1
		}
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

	private fun respond(event: SlackMessagePosted, message: String, imageUrl: String? = null) {
        if (imageUrl != null) {
            val attachment = SlackAttachment()
            attachment.title = "Employee"
            attachment.pretext = "This is the pretext."
            attachment.text = "Just testing."
            attachment.imageUrl = imageUrl
            attachment.thumbUrl = imageUrl
            attachment.color = "good"
            session.sendMessage(event.channel, message, attachment)
        } else {
            session.sendMessage(event.channel, message)
        }
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
				|$bot hex c0defeed
				|$bot dec 1234567890
				|$bot translate interest
				|
				|If you talk with me without specifying a command, I will try to answer as best as I can (maybe giving multiple answers).
				|Please try one of the following:
				|$bot 108300000012be3c
				|$bot 1083
				|$bot PORTFOLIO
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

	private fun respondStatus(event: SlackMessagePosted) {
			session.sendMessage(event.channel, """
					|${dbSchemaService.getTableNames("").size} database tables
					|${sysCodeService.findSysCodes("").size} syscodes
					|${keyMigrationService.translations.size} keymigration translations
					|${sysCodeService.translations.size} syscode translations
					|${propertiesTranslations.translations.size} properties translations
					|${translations.size} total translations
					""".trimMargin())
	}

	private fun respondStatistics(event: SlackMessagePosted) {
		var message = "Explicit commands:\n"
		for (command in explicitCommandCount.keys.sorted()) {
			message += "    $command : ${explicitCommandCount[command]}\n"
		}

		message += "Heuristic commands:\n"
		for (command in heuristicCommandCount.keys.sorted()) {
			message += "    $command : ${heuristicCommandCount[command]}\n"
		}

		session.sendMessage(event.channel, message)
	}

	private fun respondAnalyzeXentisId(event: SlackMessagePosted, id: Long, text: String, failMessage: Boolean=true): Boolean {
		session.sendMessage(event.channel, "This is a Xentis id: $text = decimal $id")

		return respondAnalyzeXentisClassPart(event, text, failMessage=failMessage)
	}

	private fun respondXentisSysCodeId(event: SlackMessagePosted, id: Long, failMessage: Boolean=true): Boolean {
		val syscode = sysCodeService.getSysCode(id)

		if (syscode == null) {
			if (failMessage) {
				session.sendMessage(event.channel, "`${id.toString(16)}` is not a valid a Xentis syscode.")
			}
			return false
		}

		session.sendMessage(event.channel, sysCodeService.toMessage(syscode))
		return true
	}

	private fun respondXentisPartialSysCodeText(event: SlackMessagePosted, text: String, failMessage: Boolean=true): Boolean {
		val syscodeResults = sysCodeService.findSysCodes(text)

		if (syscodeResults.isEmpty()) {
			if (failMessage) {
				session.sendMessage(event.channel, "No matching Xentis syscodes found for `$text`.")
			}
			return false
		}

		val syscodes = plural(syscodeResults.size, "syscode", "syscodes")
		var message = "Found ${syscodeResults.size} $syscodes:\n"

		for (syscode in syscodeResults) {
			message += "${syscode.id.toString(16)} `${syscode.name}`\n"
		}

		session.sendMessage(event.channel, message)
		return true
	}

	private fun respondXentisSysCodeText(event: SlackMessagePosted, text: String, failMessage: Boolean=true): Boolean {
		val syscodeResults = sysCodeService.findSysCodes(text)

		if (syscodeResults.isEmpty()) {
			if (failMessage) {
				session.sendMessage(event.channel, "No matching Xentis syscodes found for `$text`.")
			}
			return false
		}

		val syscodes = plural(syscodeResults.size, "syscode", "syscodes")
		var message = "Found ${syscodeResults.size} $syscodes:\n"

		limitedForLoop(10, 0, syscodeResults, { syscode ->
			message += sysCodeService.toMessage(syscode)
			message += "\n"
		}, {_ ->
			message += "..."
		})

		session.sendMessage(event.channel, message)
		return true
	}

	private fun respondAnalyzeXentisClassPart(event: SlackMessagePosted, text: String, failMessage: Boolean=true): Boolean {
		val xentisClassPartText = text.substring(0, 4)
		val xentisClassPart = java.lang.Long.parseLong(xentisClassPartText, 16) and 0xfff
		val tableName = dbSchemaService.getTableName(xentisClassPart)

		if (tableName != null) {
			session.sendMessage(event.channel, "The classpart $xentisClassPartText indicates a Xentis table `$tableName`")
			return true
		} else {
			if (failMessage) {
				session.sendMessage(event.channel, "`$xentisClassPartText` is not a Xentis classpart.")
			}
		}
		return false
	}

	private fun respondXentisTableName(event: SlackMessagePosted, text: String, failMessage: Boolean=true): Boolean {
		val tableName = text.toUpperCase()

		val table = dbSchemaService.getTable(tableName)
		if (table != null) {
			session.sendFile(event.channel, table.toMessage().toByteArray(), "TABLE_$tableName.txt")
		}

		val tableId = dbSchemaService.getTableId(tableName)
		if (tableId != null) {
			val xentisClassPartText = (tableId or 0x1000).toString(16).padStart(4, '0')
			session.sendMessage(event.channel, "The classpart of the Xentis table `$tableName` is $xentisClassPartText")
		} else {
			if (failMessage) {
				session.sendMessage(event.channel, "`$tableName` is not a Xentis table.")
			}
			return false
		}
		return true
	}

	private fun respondXentisPartialTableName(event: SlackMessagePosted, text: String, failMessage: Boolean=true): Boolean {
		val tableNames = dbSchemaService.getTableNames(text).sorted()

		if (tableNames.isEmpty()) {
			if (failMessage) {
				session.sendMessage(event.channel, "No matching tables found.")
			}
			return false
		}

		val tables = plural(tableNames.size, "table", "tables")
		var message = "_Found ${tableNames.size} matching $tables._\n"
		for(tableName in tableNames) {
			message += tableName + "\n"
		}

		session.sendMessage(event.channel, message)
		return true
	}

	private fun respondXentisKey(event: SlackMessagePosted, text: String, failMessage: Boolean=true): Boolean {
		val id = text.toIntOrNull()
		if (id == null) {
			if (failMessage) {
				session.sendMessage(event.channel, "`$text` is not a valid Xentis key id (must be an integer value).")
			}
			return false
		}

		val keyNode = keyMigrationService.getKeyNode(id)
		if (keyNode == null) {
			if (failMessage) {
				session.sendMessage(event.channel, "No Xentis key node found for id $id.")
			}
			return false
		}

		session.sendMessage(event.channel, keyMigrationService.toMessage(keyNode))
		return true
	}

	private fun respondNumberConversion(event: SlackMessagePosted, text: String, base: Int, failMessage: Boolean=true, introMessage: Boolean=true): Boolean {
		val value = text.toLongOrNull(base)

		if (value == null) {
			if (failMessage) {
				session.sendMessage(event.channel, "`$text` is not a valid number for base `$base`.")
			}
			return false
		}

		if (introMessage) {
			session.sendMessage(event.channel, "Interpreting `$text` as number with base `$base`:")
		}

		if (value < 0) {
			session.sendMessage(event.channel, """
				|Dec (unsigned): `${java.lang.Long.toUnsignedString(value, 10)}`
				|Hex (unsigned): `${java.lang.Long.toUnsignedString(value, 16)}`
				|Bin (unsigned): `${java.lang.Long.toUnsignedString(value, 2)}`
				|Dec (signed): `${value.toString(10)}`
				|Hex (signed): `${value.toString(16)}`
				|Bin (signed): `${value.toString(2)}`
				""".trimMargin())
		} else {
			session.sendMessage(event.channel, """
				|Dec: `${java.lang.Long.toUnsignedString(value, 10)}`
				|Hex: `${java.lang.Long.toUnsignedString(value, 16)}`
				|Bin: `${java.lang.Long.toUnsignedString(value, 2)}`
				""".trimMargin())
		}
		return true
	}

	private fun respondSearchTranslations(event: SlackMessagePosted, text: String, failMessage: Boolean=true): Boolean {
		if (text == "") {
			if (failMessage) {
				session.sendMessage(event.channel, "Nothing to translate.")
			}
			return false
		}

		val perfectResults = mutableSetOf<Translation>()
		val partialResults = mutableSetOf<Translation>()
		for(translation in translations) {
			if (translation.english.equals(text, ignoreCase=true)) {
				perfectResults.add(translation)
			}
			if (translation.german.equals(text, ignoreCase=true)) {
				perfectResults.add(translation)
			}
			if (translation.english.contains(text, ignoreCase=true)) {
				partialResults.add(translation)
			}
			if (translation.german.contains(text, ignoreCase=true)) {
				partialResults.add(translation)
			}
		}

		var message: String
		if (perfectResults.size > 0) {
			val translations = plural(perfectResults.size, "translation", "translations")
			message = "Found ${perfectResults.size} $translations for exactly this term:\n"
			limitedForLoop(10, 0, sortedTranslations(perfectResults), { result ->
				message += "_${result.english}_ : _${result.german}_ \n"
			}, { _ ->
				message += "...)\n"
			})
		} else if (partialResults.size > 0) {
			val translations = plural(partialResults.size, "translation", "translations")
			message = "Found ${partialResults.size} $translations that partially matched this term:\n"
			limitedForLoop(10, 0, sortedTranslations(partialResults), { result ->
				message += "_${result.english}_ : _${result.german}_ \n"
			}, { _ ->
				message += "...\n"
			})
		} else {
			message = "No translations found."
			if (!failMessage) {
				return false
			}
		}

		session.sendMessage(event.channel, message)
		return true
	}

	private fun sortedTranslations(collection: Collection<Translation>) : List<Translation> {
		val list: MutableList<Translation> = mutableListOf()
		list.addAll(collection)
		return list.sortedWith(compareBy({ it.english.length }, { it.german.length }, { it.english }, { it.german }))
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

fun String.isUpperCase(): Boolean {
	for (c in this) {
		if (!c.isUpperCase()) {
			return false
		}
	}
	return true
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
