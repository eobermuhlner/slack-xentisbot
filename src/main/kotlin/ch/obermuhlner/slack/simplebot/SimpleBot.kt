package ch.obermuhlner.slack.simplebot

import java.util.Properties
import java.io.FileReader
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener
import com.ullink.slack.simpleslackapi.SlackUser
import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy

class SimpleBot {
	val properties = loadProperties("simplebot.properties")
	val apiKey = properties.getProperty("api.key");
	val session = SlackSessionFactory.createWebSocketSlackSession(apiKey)
	val userId = loadCurrentUserId()
	
	fun start () {
		session.addMessagePostedListener(SlackMessagePostedListener { event, session ->
			if (event.sender.id != userId) {
				println(event.messageContent)
//				if (event.messageContent.startsWith(user.tag())) {
//				}
			}
		})
	}

	private fun loadCurrentUserId(): String {
		val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()

		val params = HashMap<String, String>()
		val replyHandle = session.postGenericSlackCommand(params, "auth.test")
		val reply = replyHandle.reply.plainAnswer
		
		val response = gson.fromJson(reply, AuthTestResponse::class.java)
		if (!response.ok) {
			throw SlackException(response.error)
		}
		
		//return session.findUserById(response.userId)
		return response.userId
	}
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

fun loadProperties(name: String): Properties {
	val properties = Properties()

    //val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("name")
	properties.load(FileReader(name))

	return properties
}

fun SlackUser.tag() = "<@" + this.id + ">" 

fun main(args: Array<String>) {
	val bot = SimpleBot()
	bot.start()
}
