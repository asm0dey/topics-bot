import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.InputChain
import eu.vendeli.tgbot.api.getFile
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.generated.userData
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.internal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.dnq.query.asSequence
import kotlinx.dnq.query.filter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

@InputChain
class TopicsImport {
    object Import : TopicsLink() {
        override val breakCondition: BreakCondition =
            BreakCondition { _, update, _ -> update.origin.message?.document?.fileId == null }
        override val retryAfterBreak: Boolean = false

        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot): List<Topic> {
            if (update !is MessageUpdate) return emptyList()

            val fileId =
                update.message.document?.fileId ?: return emptyList<Topic>().also {
                    message("You should upload file").send(
                        update.message.chat,
                        bot
                    )
                }

            val tgFile = getFile(fileId)
                .sendAsync(bot)
                .await()
                .getOrNull()
                ?: return message("File does not exist").send(update.message.chat, bot).let { emptyList() }
            val topics = Json.decodeFromStream<List<Topic>>(withContext(Dispatchers.IO) {
                bot.getFileContent(tgFile)!!.inputStream()
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

            return topics
        }

        override suspend fun breakAction(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            message("OK, import aborted").replyKeyboardRemove().send(update.origin.message?.chat ?: return, bot)
            bot.userData.del(update.origin.message?.chat?.id ?: return, "topics")
        }
    }

    object AcceptImport : ChainLink() {
        override val breakCondition: BreakCondition = BreakCondition { _, update, _ -> update.text != "YES" }
        override val retryAfterBreak: Boolean = false

        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            if (update !is MessageUpdate) return
            val cid = update.origin.message?.chat?.id ?: return
            val topics = Import.state.get(update.message.chat)
            store.transactional {
                XdTask.filter { it.chatId eq cid }.asSequence().forEach {
                    it.delete()
                }

                topics?.forEach {
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
            message("Aborting import").replyKeyboardRemove().send(update.origin.message?.chat ?: return, bot)
        }
    }
}