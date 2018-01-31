package ch.obermuhlner.slack.simplebot

import com.ullink.slack.simpleslackapi.events.SlackMessagePosted

class SingleArgumentCommandHandler(
        name: String,
        block: (SlackMessagePosted, String, Boolean) -> Boolean)
    : CommandHandler(name, { event, args, heuristic ->
        if (args.isNotEmpty()) {
            block.invoke(event, args[0], heuristic)
        } else {
            false
        }
    })
