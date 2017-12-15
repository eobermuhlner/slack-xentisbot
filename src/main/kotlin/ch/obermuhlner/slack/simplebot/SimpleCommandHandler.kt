package ch.obermuhlner.slack.simplebot

import com.ullink.slack.simpleslackapi.events.SlackMessagePosted

class SimpleCommandHandler(
        name: String,
        block: (SlackMessagePosted, String, Boolean) -> Unit)
    : CommandHandler(name, { event, args, heuristic ->
        if (args.isNotEmpty()) {
            block.invoke(event, args[0], heuristic)
        }
    })
