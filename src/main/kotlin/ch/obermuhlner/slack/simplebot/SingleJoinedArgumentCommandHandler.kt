package ch.obermuhlner.slack.simplebot

import com.ullink.slack.simpleslackapi.events.SlackMessagePosted

class SingleJoinedArgumentCommandHandler(
        name: String,
        block: (SlackMessagePosted, String, Boolean) -> Boolean)
    : CommandHandler(name, { event, args, heuristic ->
        if (args.isNotEmpty()) {
            val joiner = java.util.StringJoiner(" ")
            for (arg in args) {
                joiner.add(arg)
            }
            block.invoke(event, joiner.toString(), heuristic)
        } else {
            false
        }
    })
