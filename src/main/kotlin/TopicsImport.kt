import eu.vendeli.tgbot.annotations.WizardHandler
import eu.vendeli.tgbot.api.media.getFile
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.chain.Transition
import eu.vendeli.tgbot.types.chain.WizardContext
import eu.vendeli.tgbot.types.chain.WizardStep
import eu.vendeli.tgbot.types.component.MessageUpdate
import eu.vendeli.tgbot.types.component.getChat
import eu.vendeli.tgbot.types.component.getOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.dnq.query.asSequence
import kotlinx.dnq.query.filter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@WizardHandler(trigger = ["/import"])
object ImportWizard {
    object Upload : WizardStep(isInitial = true) {
        override suspend fun onEntry(ctx: WizardContext) {
            message("Please upload previously exported file or type 'abort' to abort import")
                .send(ctx.update.getChat().id, ctx.bot)
        }

        override suspend fun validate(ctx: WizardContext): Transition {
            val chatId = ctx.update.getChat().id
            val fileId = (ctx.update as? MessageUpdate)?.message?.document?.fileId
            if (fileId == null) {
                message("OK, import aborted").replyKeyboardRemove().send(chatId, ctx.bot)
                return Transition.Finish
            }
            return Transition.Next
        }

        // ponytail: topics serialized to JSON string so the default StringStateManager can persist them
        override suspend fun store(ctx: WizardContext): String {
            val fileId = (ctx.update as MessageUpdate).message.document!!.fileId
            val tgFile = getFile(fileId).sendReturning(ctx.bot).await().getOrNull() ?: return "[]"
            val bytes = withContext(Dispatchers.IO) { ctx.bot.getFileContent(tgFile) } ?: return "[]"
            return Json.encodeToString(Json.decodeFromString<List<Topic>>(bytes.decodeToString()))
        }
    }

    object Confirm : WizardStep() {
        override suspend fun onEntry(ctx: WizardContext) {
            val chatId = ctx.update.getChat().id
            val topics = topicsOf(ctx)
            if (topics.isEmpty()) {
                message("File does not exist or is empty. Import aborted.").send(chatId, ctx.bot)
                return
            }
            message("Going to delete all topics and rewrite with new ones (${topics.size} in total)")
                .forceReply(selective = true)
                .replyKeyboardMarkup {
                    +"YES"
                    +"NO"
                }
                .send(chatId, ctx.bot)
        }

        override suspend fun validate(ctx: WizardContext): Transition {
            val cid = ctx.update.getChat().id
            val topics = topicsOf(ctx)
            if (topics.isEmpty()) return Transition.Finish
            if (ctx.update.text != "YES") {
                message("Aborting import").replyKeyboardRemove().send(cid, ctx.bot)
                return Transition.Finish
            }
            store.transactional {
                XdTask.filter { it.chatId eq cid }.asSequence().forEach { it.delete() }
                topics.forEach {
                    XdTask.new {
                        createdAt = it.createdAt
                        author = it.author
                        authorName = it.authorName
                        text = it.text
                        chatId = cid
                    }
                }
            }
            message("Done. Updated topics list:").replyKeyboardRemove().send(cid, ctx.bot)
            topics(ctx.bot, ctx.update as MessageUpdate)
            return Transition.Finish
        }
    }

    private suspend fun topicsOf(ctx: WizardContext): List<Topic> =
        Json.decodeFromString(ctx.getState(Upload::class) as? String ?: "[]")
}
