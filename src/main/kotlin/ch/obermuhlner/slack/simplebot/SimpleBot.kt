package ch.obermuhlner.slack.simplebot

import ch.obermuhlner.slack.simplebot.TranslationService.Translation
import ch.obermuhlner.slack.simplebot.xentis.*
import java.util.Properties
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import com.ullink.slack.simpleslackapi.SlackUser
import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy
import com.ullink.slack.simpleslackapi.SlackAttachment
import com.ullink.slack.simpleslackapi.SlackChannel
import com.ullink.slack.simpleslackapi.SlackSession
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted
import com.ullink.slack.simpleslackapi.impl.ChannelHistoryModuleFactory
import java.util.regex.Pattern
import java.time.*
import java.time.format.DateTimeParseException
import com.jcabi.ssh.Shell
import com.jcabi.ssh.SshByPassword
import org.ccil.cowan.tagsoup.jaxp.SAXParserImpl
import org.xml.sax.Attributes
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.*
import java.net.URL
import javax.xml.parsers.SAXParserFactory

class SimpleBot(
		private val sysCodeService: SysCodeService = XentisSysCodeService(),
		private val propertiesTranslations: PropertiesTranslationService = XentisPropertiesTranslationService(),
		private val dbSchemaService: DbSchemaService = XentisDbSchemaService(),
		private val keyMigrationService: KeyMigrationService = XentisKeyMigrationService(),
		private var xentisServerSearchHostnames: List<String> = listOf(),
		private var xentisServerSearchCommand: String = "xentis stat",
		private var xentisServerPrefixHost: String = "pdvmapp",
		private var xentisServerPrefixCommand: String = "xentis stat") {

	private lateinit var session: SlackSession
	private lateinit var user: SlackUser
	private var adminUser: SlackUser? = null

	private val observedChannelIds = HashSet<String>()

	private val translations = mutableSetOf<Translation>()

	private val startMilliseconds = System.currentTimeMillis()
	private val explicitCommandCount : MutableMap<String, Int> = mutableMapOf()
	private val heuristicCommandCount : MutableMap<String, Int> = mutableMapOf()
	private val userCommandCount : MutableMap<String, Int> = mutableMapOf()

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
			}, CommandHandler("delete-all-messages") { event, _, heuristic ->
				if (!heuristic) {
					deleteAllMessages(event.channel)
					true
				} else {
					false
				}
			}, SingleArgumentCommandHandler("id") { event, arg, heuristic ->
				val xentisId = parseXentisId(arg)
				if (xentisId != null) {
					respondAnalyzeXentisId(event, xentisId.first, xentisId.second, failMessage = !heuristic)
				} else {
					if (!heuristic) {
						respond(event, "`$arg` is a not a valid Xentis id. It must be 16 hex digits.")
					}
					false
				}
			}, SingleJoinedArgumentCommandHandler("syscodes") { event, arg, heuristic ->
				if (!heuristic) {
					respondXentisPartialSysCodeText(event, arg, failMessage = !heuristic)
				} else {
					false
				}
			}, SingleJoinedArgumentCommandHandler("syscode") { event, arg, heuristic ->
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
			}, SingleArgumentCommandHandler("classpart") { event, arg, heuristic ->
				val xentisId = parseXentisId(arg, 4)
				if (xentisId != null) {
					respondAnalyzeXentisClassPart(event, xentisId.second, failMessage = !heuristic)
				} else {
					if (!heuristic) {
						respond(event, "`$arg` is a not a valid Xentis classpart. It must be 4 hex digits.")
					}
					false
				}
			}, SingleArgumentCommandHandler("tables") { event, arg, heuristic ->
				if (!heuristic || arg.isUpperCase()) {
					respondXentisPartialTableName(event, arg, failMessage = !heuristic)
				} else {
					false
				}
			}, SingleArgumentCommandHandler("table") { event, arg, heuristic ->
				if (!heuristic || arg.isUpperCase()) {
					respondXentisTableName(event, arg, failMessage = !heuristic)
				} else {
					false
				}
			}, SingleArgumentCommandHandler("key") { event, arg, heuristic ->
				respondXentisKey(event, arg, failMessage = !heuristic)
			}, SingleArgumentCommandHandler("dec") { event, arg, heuristic ->
				if (!heuristic) {
					respondNumberConversion(event, arg.removeSuffix("L"), 10, introMessage = false)
				} else {
					false
				}
			}, SingleArgumentCommandHandler("hex") { event, arg, heuristic ->
				if (!heuristic) {
					if (arg.startsWith("-0x")) {
						respondNumberConversion(event, "-" + arg.removePrefix("-0x").removeSuffix("L"), 16, introMessage = false)
					} else {
						respondNumberConversion(event, arg.removePrefix("0x").removeSuffix("L"), 16, introMessage = false)
					}
				} else {
					false
				}
			}, SingleArgumentCommandHandler("bin") { event, arg, heuristic ->
				if (!heuristic) {
					if (arg.startsWith("-0b")) {
						respondNumberConversion(event, "-" + arg.removePrefix("-0b").removeSuffix("L"), 2, introMessage = false)
					} else {
						respondNumberConversion(event, arg.removePrefix("0b").removeSuffix("L"), 2, introMessage = false)
					}
				} else {
					false
				}
			}, SingleArgumentCommandHandler("number") { event, arg, heuristic ->
				if (arg.startsWith("0x")) {
					respondNumberConversion(event, arg.removePrefix("0x").removeSuffix("L"), 16, failMessage = !heuristic, introMessage = false)
				} else if (arg.startsWith("-0x")) {
					respondNumberConversion(event, "-" + arg.removePrefix("-0x").removeSuffix("L"), 16, failMessage = !heuristic, introMessage = false)
				} else if (arg.startsWith("0b")) {
					respondNumberConversion(event, arg.removePrefix("0b").removeSuffix("L"), 2, failMessage = !heuristic, introMessage = false)
				} else if (arg.startsWith("-0b")) {
					respondNumberConversion(event, "-" + arg.removePrefix("-0b").removeSuffix("L"), 2, failMessage = !heuristic, introMessage = false)
				} else {
					var success = false
					success = success or respondNumberConversion(event, arg.removeSuffix("L"), 10, failMessage = !heuristic)
					success = success or respondNumberConversion(event, arg.removeSuffix("L"), 16, failMessage = !heuristic)
					success = success or respondNumberConversion(event, arg.removeSuffix("L"), 2, failMessage = !heuristic)
					success
				}
            }, CommandHandler("millis") { event, args, heuristic ->
                if (!heuristic) {
                    var success = false
                    if (!success) {
                        if (args.isEmpty()) {
                            val dateTime = LocalDateTime.now()
                            val dateTimeAsMillis = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
                            respond(event, "The current date time is `$dateTime` (in ${ZoneOffset.systemDefault()}) which corresponds to `$dateTimeAsMillis` milliseconds since epoch (1970-01-01).")
                            success = true
                        }
                    }
                    if (!success) {
                        val millis = args[0].toLongOrNull()
                        if (millis != null) {
                            val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.of("UTC"))
                            respond(event, "The decimal value `$millis` interpreted as milliseconds since epoch (1970-01-01) corresponds to `$dateTime` in UTC.")
                            success = true
                        }
                    }
                    if (!success) {
                        try {
                            val dateTime = LocalDateTime.parse(args[0])
                            val dateTimeAsMillis = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
                            respond(event, "The UTC date time `$dateTime` corresponds to `$dateTimeAsMillis` milliseconds since epoch (1970-01-01).")
                            success = true
                        } catch (ex: DateTimeParseException) {
                            // ignore
                        }
                    }
                    if (!success) {
                        try {
                            val date = LocalDate.parse(args[0])
                            val dateAsMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                            respond(event, "The UTC date `$date` corresponds to `$dateAsMillis` milliseconds since epoch (1970-01-01).")
                            success = true
                        } catch (ex: DateTimeParseException) {
                            // ignore
                        }
                    }
                    if (!success) {
                        respond(event, """
                                    |`${args[0]}` is not a valid value for milliseconds/date time conversion.
                                    |It must be either a milliseconds value (like `${System.currentTimeMillis()}`)
                                    |or a date like `${LocalDate.now()}`
                                    |or a date time like `${LocalDateTime.now()}`.""".trimMargin())
                        success = true
                    }
                    success
                } else {
                    false
                }
            }, SingleArgumentCommandHandler("pd") { event, arg, heuristic ->
                if (!heuristic) {
                    respond(event, "Profidata $arg", imageUrl = "http://pdintra/php/mafotos_virt/$arg.jpg")
					true
                } else {
					false
				}
			}, SingleArgumentCommandHandler("image") { event, arg, heuristic ->
				if (!heuristic) {
					respond(event, "Image $arg", imageUrl = arg)
					true
				} else {
					false
				}
            }, SingleArgumentCommandHandler("xentis") { event, arg, heuristic ->
                if (!heuristic) {
                    respondXentisServerStatus(event, arg)
                    true
                } else {
                    false
                }
            }, SingleArgumentCommandHandler("db") { event, arg, heuristic ->
                if (!heuristic) {
                    respondXentisDbStatus(event, arg)
                    true
                } else {
                    false
                }
            }, SingleJoinedArgumentCommandHandler("translate") { event, arg, _ ->
               respondSearchTranslations(event, arg)
			}
		)

	private fun deleteAllMessages(channel: SlackChannel) {
		val channelHistory = ChannelHistoryModuleFactory.createChannelHistoryModule(session)
		val messages = channelHistory.fetchHistoryOfChannel(channel.name)
		println("Found ${messages.size} messages in the history - deleting them")
		for (message in messages) {
			session.deleteMessage(message.timestamp, channel)
		}
		println("Finished deleting")
	}

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
		xentisServerSearchHostnames = properties.getProperty("xentis.server.search.hosts").split(",").map {it.trim()}
		xentisServerSearchCommand = properties.getProperty("xentis.server.search.command", xentisServerSearchCommand)
		xentisServerPrefixHost = properties.getProperty("xentis.server.prefix.host", xentisServerPrefixHost)
		xentisServerPrefixCommand = properties.getProperty("xentis.server.prefix.command", xentisServerPrefixCommand)

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

		try {
			for (commandHandler in commandHandlers) {
				val done = commandHandler.execute(event, args)
				if (done) {
					incrementCommandCount(commandHandler, false)
					return
				}
			}

			for (commandHandler in commandHandlers) {
				val done = commandHandler.execute(event, args, true)
				if (done) {
					incrementCommandCount(commandHandler, true)
				}
			}
		} finally {
			incrementUserCommandCount(event.user.realName)
		}
	}

	private fun incrementUserCommandCount(userName : String) {
		userCommandCount[userName] = userCommandCount.getOrDefault(userName, 0) + 1
	}

	private fun incrementCommandCount(commandHandler: CommandHandler, heuristic: Boolean) {
		if (heuristic) {
			heuristicCommandCount[commandHandler.name] = heuristicCommandCount.getOrDefault(commandHandler.name, 0) + 1
		} else {
			explicitCommandCount[commandHandler.name] = explicitCommandCount.getOrDefault(commandHandler.name, 0) + 1
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
				|$bot millis -11676096000000
				|$bot millis 1600-01-01
				|$bot hex c0defeed
				|$bot dec 1234567890
				|$bot xentis intui
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
				|millis 1600-01-01
				|xentis intui
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

	private val millisecondsPerSecond = 1000
	private val millisecondsPerMinute = 60 * millisecondsPerSecond
	private val millisecondsPerHour = 60 * millisecondsPerMinute
	private val millisecondsPerDay = 24 * millisecondsPerHour

	private fun respondStatistics(event: SlackMessagePosted) {
		val daysHoursMinutesSecondsMilliseconds = convertMillisecondsToDaysHoursMinutesSecondsMilliseconds(System.currentTimeMillis() - startMilliseconds)
		val runningDays = daysHoursMinutesSecondsMilliseconds[0]
		val runningHours = daysHoursMinutesSecondsMilliseconds[1]
		val runningMinutes = daysHoursMinutesSecondsMilliseconds[2]
		val runningSeconds = daysHoursMinutesSecondsMilliseconds[3]

		var message = "This bot is running since $runningDays days $runningHours hours $runningMinutes minutes $runningSeconds seconds.\n"

		message += "Explicit commands:\n"
		for (command in explicitCommandCount.keys.sorted()) {
			message += "    $command : ${explicitCommandCount[command]}\n"
		}

		message += "Heuristic commands:\n"
		for (command in heuristicCommandCount.keys.sorted()) {
			message += "    $command : ${heuristicCommandCount[command]}\n"
		}

		message += "User commands:\n"
		for (userName in userCommandCount.keys.sorted()) {
			message += "    $userName : ${userCommandCount[userName]}\n"
		}

		session.sendMessage(event.channel, message)
	}

	private fun convertMillisecondsToDaysHoursMinutesSecondsMilliseconds(milliseconds: Long): List<Long> {
		var remainingMilliseconds = milliseconds
		val days = remainingMilliseconds / millisecondsPerDay

		remainingMilliseconds -= days * millisecondsPerDay
		val hours = remainingMilliseconds / millisecondsPerHour

		remainingMilliseconds -= hours * millisecondsPerHour
		val minutes = remainingMilliseconds / millisecondsPerMinute

		remainingMilliseconds -= minutes * millisecondsPerMinute
		val seconds = remainingMilliseconds / millisecondsPerSecond

		remainingMilliseconds -= seconds * millisecondsPerSecond

		return listOf(days, hours, minutes, seconds, remainingMilliseconds)
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

	private fun respondXentisServerStatus(event: SlackMessagePosted, name: String): Boolean {
		var success = false

        if (name.startsWith(xentisServerPrefixHost)) {
            try {
				respond(event, "Checking xentis server $name")
                val shell = SshByPassword(name, 22, "xen", "xen")
				val response = Shell.Plain(shell).exec(xentisServerPrefixCommand)
                respond(event, """
    				|User `xen` on host `$name` responded with:
    				|```$response```""".trimMargin())
                success = true
            } catch (ex: Exception) {
				respond(event, "User `xen` on host `$name` failed with ${ex.javaClass.simpleName} ${ex.message}")
            }
        } else {
            // old xentis servers
            for (hostname in xentisServerSearchHostnames) {
                try {
                    val shell = SshByPassword(hostname, 22, name, name)
                    val response = Shell.Plain(shell).exec(xentisServerSearchCommand)
                    respond(event, """
    				|User `$name` on host `$hostname` responded with:
    				|```$response```""".trimMargin())
                    success = true
                } catch (ex: Exception) {
                    if (ex.message == "com.jcraft.jsch.JSchException: Auth cancel") {
                        // ignore
                    } else {
                        respond(event, "User `$name` on host `$hostname` failed with ${ex.javaClass.simpleName} ${ex.message}")
                    }
                }
            }
			if (!success) {
				respond(event, "No user `$name` found on any of the hosts ${xentisServerSearchHostnames.joinToString(", ", "`", "`")}.")
			}
		}

		return success
	}

    private fun respondXentisDbStatus(event: SlackMessagePosted, name: String): Boolean {
        var success = false

		val message = StringBuilder()
		val line = StringBuilder()
		var tdClass: String? = null

        val classPadding: Map<String, Int> = mapOf(
                "servicename" to 7,
                "schemaname" to 30,
                "size" to 6,
                "impdat" to 8,
                "responsible" to 10,
                "locked" to 10,
                "comment" to 30)
        val classLeftPadding: Set<String> = setOf("size")

        val handler = object : DefaultHandler() {
            private var insideTr: Boolean = false
            private var insideTd: Boolean = false

            override fun startElement(uri: String?, localName: String, qName: String, attributes: Attributes) {
                if (localName == "tr") {
                    insideTr = true
                }
                if (insideTr && localName == "td") {
                    insideTd = true
					tdClass = attributes.getValue("class")
                }
            }

            override fun endElement(uri: String, localName: String, qName: String) {
                if (localName == "tr") {
                    insideTr = false

					if (line.contains(name)) {
						message.append(line)
						message.append("\n")
						if (message.length > 3000) {
							respond(event, "```\n$message\n```")
							success = true
							message.setLength(0)
						}
					}
					line.setLength(0)
                }
                if (insideTr && localName == "td") {
                    insideTd = false
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
				val text = String(ch, start, length)
				if (classPadding.containsKey(tdClass) && text != "N.A.") {
                    val paddedText = if (classLeftPadding.contains(tdClass)) {
                            text.padStart(classPadding[tdClass]!!)
                        } else {
                            text.padEnd(classPadding[tdClass]!!)
                        }
                    line.append(paddedText)
                    line.append(" ")
				}
            }
        }

        val url = URL("http://dbschemas/")
		val parser = SAXParserImpl.newInstance(null)
        parser.parse(url.openStream(), handler)

		if (message.isNotEmpty()) {
			respond(event, "```\n$message\n```")
			success = true
		} else {
            session.sendMessage(event.channel, "No database found.")
        }

		return success
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

		if (perfectResults.size > 0) {
			val translations = plural(perfectResults.size, "translation", "translations")
			var message = "Found ${perfectResults.size} $translations for exactly this term:\n"
			limitedForLoop(10, 0, sortedTranslations(perfectResults), { result ->
				message += "_${result.english}_ : _${result.german}_ \n"
			}, { _ ->
				message += "...)\n"
			})
			session.sendMessage(event.channel, message)
		}

		if (partialResults.size > perfectResults.size) {
			val translations = plural(partialResults.size, "translation", "translations")
			var message = "Found ${partialResults.size} $translations that partially matched this term:\n"
			limitedForLoop(10, 0, sortedTranslations(partialResults), { result ->
				message += "_${result.english}_ : _${result.german}_ \n"
			}, { _ ->
				message += "...\n"
			})
			session.sendMessage(event.channel, message)
		} else {
			var message = "No translations found."
			if (!failMessage) {
				return false
			}
			session.sendMessage(event.channel, message)
		}
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
