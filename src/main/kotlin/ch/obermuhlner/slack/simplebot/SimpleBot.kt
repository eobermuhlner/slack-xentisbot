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

	val translations = loadPropertiesTranslations()
	
	fun start () {
		session.addMessagePostedListener(SlackMessagePostedListener { event, _ ->
			if (event.sender.id != user.id) {
				println(event.messageContent)
				val userTag = user.tag()
				if (event.messageContent.startsWith(userTag)) {
					val strippedMessageContent = event.messageContent.substring(userTag.length)
					respondToMessage(event, strippedMessageContent) 
				} else if (observedChannelIds.contains(event.channel.id)) {
					respondToMessage(event, event.messageContent) 
				}
			}
		})
	}
	
	fun respondToMessage(event: SlackMessagePosted , messageContent: String) {
		val commandArgs = messageContent.trim().split(" ")
		if (commandArgs.size == 0) {
			return
		}
		
		val command = commandArgs[0]
		when (command) {
			"observe" -> {
				observedChannelIds.add(event.channel.id)
				session.sendMessage(event.channel, "Observing channel. Observing now ${observedChannelIds.size} channels.");
			}
			"ignore" -> {
				observedChannelIds.remove(event.channel.id)
				session.sendMessage(event.channel, "Ignoring channel. Observing now ${observedChannelIds.size} channels.");
			}
			"search" -> {
				search(event, commandArgs)
			}
			else -> {
				session.sendMessage(event.channel, messageContent + " (REPLY)")
			}
		}
	}
	
	private fun search(event: SlackMessagePosted, args: List<String>) {
		val perfectResults = HashSet<String>()
		val partialResults = HashSet<String>()
		for((source, target) in translations) {
			if (source == args[1]) {
				perfectResults.add(target)
			}
			if (target == args[1]) {
				perfectResults.add(source)
			}
			if (source.contains(args[1])) {
				partialResults.add(target)
			}
			if (target.contains(args[1])) {
				partialResults.add(source)
			}
		}

		var message = ""
		if (perfectResults.size > 0) {
			message = "_Found ${perfectResults.size} perfect matches:_\n"
			for (result in perfectResults) {
				message += result + "\n"
			}
		} else if (partialResults.size > 0) {
			message = "_Found ${partialResults.size} partial matches:_\n"
			for (result in partialResults) {
				message += result + "\n"
			}
		} else {
			message = "_Nothing found._"
		}
		
		session.sendMessage(event.channel, message)
	}

	private fun loadPropertiesTranslations(): List<Pair<String, String>> {
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
						result.add(Pair(translations1.getProperty(key), translations2.getProperty(key)))
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
