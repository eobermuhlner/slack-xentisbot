package ch.obermuhlner.slack.simplebot

import com.ullink.slack.simpleslackapi.events.SlackMessagePosted

open class CommandHandler(val name: String, private val block: (SlackMessagePosted, List<String>, Boolean) -> Boolean) {

    fun execute(event: SlackMessagePosted, args: List<String>, heuristic: Boolean = false): Boolean {
        if (args.isNotEmpty() && args[0] == name) {
            return block.invoke(event, args.slice(1 .. args.lastIndex), false)
        } else if (heuristic) {
            return block.invoke(event, args, true)
        }
        return false
    }
}
