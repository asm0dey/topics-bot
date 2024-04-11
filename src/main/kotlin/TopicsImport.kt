import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.InputChain
import eu.vendeli.tgbot.api.getFile
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.internal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.dnq.query.asSequence
import kotlinx.dnq.query.filter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.net.URI

@InputChain
class TopicsImport {
    object Import : ChainLink() {
        override val breakCondition: BreakCondition =
            BreakCondition { _, update, _ -> update.update.message?.document?.fileId == null }
        override val retryAfterBreak: Boolean = false

        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            if (update !is MessageUpdate) return

            val fileId =
                update.message.document?.fileId ?: return message("You should upload file").send(
                    update.message.chat,
                    bot
                )
            val filePath = getFile(fileId)
                .sendAsync(bot)
                .await()
                .getOrNull()
                ?.filePath
                ?: return message("File does not exist").send(update.message.chat, bot)
            val topics = Json.decodeFromStream<List<Topic>>(withContext(Dispatchers.IO) {
                URI("https://api.telegram.org/file/bot${config.bot.token.value}/$filePath").toURL().openStream()
                    .buffered()
            })
            message(
                "Going to delete all topics and rewrite with new ones (${topics.size} in total)"
            )
                .forceReply(selective = true)
                .replyKeyboardMarkup {
                    +"YES"
                    +"NO"
                }
                .send(update.message.chat, bot)
            bot.userData.set(update.message.chat.id, "topics", topics)
        }

        override suspend fun breakAction(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            message("OK, import aborted").replyKeyboardRemove().send(update.update.message?.chat ?: return, bot)
            bot.userData.del(update.update.message?.chat?.id ?: return, "topics")
        }
    }

    object AcceptImport : ChainLink() {
        override val breakCondition: BreakCondition = BreakCondition { _, update, _ -> update.text != "YES" }
        override val retryAfterBreak: Boolean = false

        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            if (update !is MessageUpdate) return
            val cid = update.update.message?.chat?.id ?: return
            store.transactional {
                XdTask.filter { it.chatId eq cid }.asSequence().forEach {
                    it.delete()
                }
                bot.userData.get<List<Topic>>(update.message.chat.id, "topics")?.forEach {
                    XdTask.new {
                        createdAt = it.createdAt
                        author = it.author
                        authorName = it.authorName
                        text = it.text
                        chatId = update.message.chat.id
                    }
                }
            }
            message("Done. Updated topics list:").replyKeyboardRemove().send(cid, bot)
            topics(bot, update)
        }

        override suspend fun breakAction(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            message("Aborting import").replyKeyboardRemove().send(update.update.message?.chat ?: return, bot)
        }
    }
}