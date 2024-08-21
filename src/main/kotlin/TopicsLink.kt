import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.chat.Chat
import eu.vendeli.tgbot.types.internal.chain.StatefulLink

abstract class TopicsLink : StatefulLink<List<Topic>>()

fun Chat.asUser() = User(id, false, "")