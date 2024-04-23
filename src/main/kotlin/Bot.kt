import CleanDB.Companion.DB_DELETE_NO
import CleanDB.Companion.DB_DELETE_YES
import TopicsImport.Import
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.annotations.RegexCommandHandler
import eu.vendeli.tgbot.annotations.UnprocessedHandler
import eu.vendeli.tgbot.api.media.sendMediaGroup
import eu.vendeli.tgbot.api.message.deleteMessages
import eu.vendeli.tgbot.api.message.editMessageText
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.ParseMode.MarkdownV2
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.internal.*
import eu.vendeli.tgbot.types.media.InputMedia
import eu.vendeli.tgbot.utils.builders.InlineKeyboardMarkupBuilder
import eu.vendeli.tgbot.utils.setChain
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.coroutines.delay
import kotlinx.dnq.XdModel
import kotlinx.dnq.query.*
import kotlinx.dnq.store.container.StaticStoreContainer
import kotlinx.dnq.util.initMetaData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.joda.time.DateTime
import org.joda.time.LocalDateTime
import kotlin.text.Charsets.UTF_8
import kotlin.time.Duration.Companion.seconds

suspend fun main() {
    val bot = TelegramBot(config.bot.token.value)

    bot.handleUpdates()
    // start long-polling listener
}

fun initXodus(): TransientEntityStore {
    XdModel.registerNodes(XdTask)

    val databaseHome = config.database.location

    val store = StaticStoreContainer.init(
        dbFolder = databaseHome,
        entityStoreName = "db"
    )

    initMetaData(XdModel.hierarchy, store)

    return store
}

val store by lazy { initXodus() }


@RegexCommandHandler("/addtopic ?.*", options = [RegexOption.DOT_MATCHES_ALL])
suspend fun addtopic(user: User, bot: TelegramBot, update: MessageUpdate) {
    store.transactional {
        XdTask.new {
            text = update.text.substringAfter("/addtopic").replace("@newpodcast2_topic_manager_bot", "").trim()
            author = user.id
            createdAt = DateTime.now()
            authorName = user.username ?: listOfNotNull(user.firstName, user.lastName).joinToString(" ")
            chatId = update.message.chat.id
        }
    }
    message { "Added. Thanks ${user.username}" }.send(update.message.chat.id, bot)
}

@RegexCommandHandler("/broadcast ?.*", options = [RegexOption.DOT_MATCHES_ALL])
suspend fun broadcast(bot: TelegramBot, up: MessageUpdate) {
    if (up.user.id != config.bot.admin) return
    val chatsToSend = store.transactional {
        XdTask.filter { it.chatId ne up.message.chat.id }.asSequence().map { it.chatId }.distinct().toList()
    }
    for (chat in chatsToSend) {
        message(up.message.text ?: return).send(chat, bot)
    }
}

private const val TOPIC_PREFIX = "topics;"
private const val PAGE_SIZE = 9

@CommandHandler(["/topics"])
suspend fun topics(bot: TelegramBot, upd: MessageUpdate) {
    val topicsCount = countTopicsInChat(upd.message.chat.id)
    val (ids, text) = store.transactional {
        val toList = chatTopics(upd.message.chat.id)
            .take(PAGE_SIZE)
            .toList()
        toList.map { it.xdId } to toList.textTopics()
    }

    message(text)
        .options { parseMode = MarkdownV2 }
        .inlineKeyboardMarkup { firstPageButtons(topicsCount, ids) }
        .sendAsync(to = upd.message.chat.id, via = bot)
        .await()
        .getOrNull()
}

private fun countTopicsInChat(chatId: Long): Int =
    store.transactional { XdTask.filter { it.chatId eq chatId }.size() }

private fun Iterable<XdTask>.textTopics(page: Int = 0) =
    if (none()) "No more tasks :("
    else mapIndexed { index, xdTask ->
        "${(index + 1).toString().padStart(2, '0')}. \uD83D\uDCCC" +
                "${xdTask.text} by [${xdTask.authorName}](tg://user?=${xdTask.author}) _(${xdTask.createdAt.toLocalDate()})_"
    }
        .joinToString(
            "\n", postfix = """
---
Page ${page + 1}"""
        )

@CommandHandler(["/start"])
suspend fun start(user: User, bot: TelegramBot) {
    message { "Well hello hello" }.send(user, bot)
}


@CommandHandler(["/cleandb"])
suspend fun cleandb(bot: TelegramBot, up: MessageUpdate, user: User) {
    if (user.id == config.bot.admin) {
        message { "Sure?" }.inlineKeyboardMarkup {
            callbackData("yes") { DB_DELETE_YES }
            callbackData("NO") { DB_DELETE_NO }
        }
            .send(up.message.chat, bot)
        bot.inputListener.setChain(up.user, CleanDB.Try)
        bot.userData[up.user, "deletingInChat"] = up.message.chat.id
    } else message { "Only the bot owner can do it, bro" }.send(up.message.chat, bot)
}

@CommandHandler(["/myid"])
suspend fun myid(bot: TelegramBot, user: User) {
    message { "`${user.id}`" }.options { parseMode = MarkdownV2 }.send(user, bot)
}

@RegexCommandHandler(value = "$TOPIC_PREFIX.*", options = [RegexOption.DOT_MATCHES_ALL])
suspend fun updateTopicsMessage(bot: TelegramBot, up: CallbackQueryUpdate) {
    val chatId = up.callbackQuery.message?.chat?.id ?: return
    val topicsCount = countTopicsInChat(chatId)
    val messageId = up.callbackQuery.message?.messageId
    val pagePrefix = "${TOPIC_PREFIX}page="
    if (up.text.startsWith(pagePrefix)) {
        val page = up.text.substringAfter(pagePrefix).toInt()
        val (ids, topics) = store.transactional {
            val q = chatTopics(chatId).drop(PAGE_SIZE * page).take(PAGE_SIZE)
            q.asIterable().map { it.xdId } to q.asIterable().textTopics(page)
        }

        editMessageText(messageId ?: return) { topics }
            .options { parseMode = MarkdownV2 }
            .inlineKeyboardMarkup {
                if (page != 0)
                    callbackData("<< Prev") { "${TOPIC_PREFIX}page=${page - 1}" }
                if (PAGE_SIZE * page + PAGE_SIZE < topicsCount)
                    callbackData("Next >>") { "${TOPIC_PREFIX}page=${page + 1}" }
                if (page != 0 || PAGE_SIZE < topicsCount)
                    br()
                callbackData("Refresh") { "${TOPIC_PREFIX}refresh" }
                br()
                callbackData("Delete") { "deleteMany=${ids.joinToString("&")}" }
            }.send(chatId, bot)
    } else if (up.text == "${TOPIC_PREFIX}refresh") {
        val (ids, text) = store.transactional {
            val tasks = chatTopics(chatId)
                .take(PAGE_SIZE)
                .asIterable()
            tasks.map { it.xdId } to tasks.textTopics()
        }
        editMessageText(messageId ?: return) { text }
            .options { parseMode = MarkdownV2 }
            .inlineKeyboardMarkup { firstPageButtons(topicsCount, ids) }
            .send(chatId, bot)
    }
}

@RegexCommandHandler("deleteMany=.*",options = [RegexOption.DOT_MATCHES_ALL])
suspend fun deleteMany(bot: TelegramBot, up: CallbackQueryUpdate) {
    val ids: List<String> = up.text.substringAfter("deleteMany=").split("&")
    val chatId = up.callbackQuery.message?.chat?.id ?: return
    message("Which topic do you want to delete?")
        .inlineKeyboardMarkup {
            store.transactional {
                XdTask.filter { it.chatId eq chatId }.toList().filter { it.xdId in ids }.map { it.xdId to it.text }
            }
                .forEach { (id, text) ->
                    callbackData(text) { "delete=${id}" }
                    br()
                }
        }
        .send(chatId, bot)
}

@CommandHandler(["/export"])
suspend fun export(bot: TelegramBot, up: MessageUpdate) {
    val chat = up.message.chat
    val allTopics = store.transactional {
        chatTopics(chat.id).toList().map { Topic(it) }
    }
    val data = Json.encodeToString(allTopics)
    sendMediaGroup(
        InputMedia.Document(
            ImplicitFile.InpFile(
                InputFile(
                    data.toByteArray(UTF_8),
                    "export-${up.message.chat.id}-${LocalDateTime.now()}.json",
                    "application/json"
                )
            )
        )
    ).send(chat, bot)
}

@CommandHandler(["/import"])
suspend fun import(bot: TelegramBot, up: MessageUpdate) {
    message("Please upload previously exported file or type 'abort' to abort import").send(up.message.chat, bot)
    bot.inputListener.setChain(up.user, Import)
}

@RegexCommandHandler("delete=.*",options = [RegexOption.DOT_MATCHES_ALL])
suspend fun delete(bot: TelegramBot, up: CallbackQueryUpdate) {
    val chatId = up.callbackQuery.message?.chat?.id ?: return
    val id = up.text.substringAfter("delete=")
    val sourceMessageId = up.callbackQuery.message?.messageId
    store.transactional {
        XdTask.filter { it.chatId eq chatId }.toList().singleOrNull { it.xdId == id }?.let {
            it.finishedAt = DateTime.now()
        }
    }
    sourceMessageId?.let { deleteMessages(it).send(chatId, bot) }
    message("Don't forget to refresh the task List").sendAsync(chatId, bot).await().getOrNull()?.messageId?.let {
        delay(10.seconds)
        deleteMessages(it).send(chatId, bot)
    }
}

private fun chatTopics(chatId: Long) = XdTask
    .filter { it.chatId eq chatId and (it.finishedAt eq null) }
    .sortedBy(XdTask::createdAt, asc = true)

private fun InlineKeyboardMarkupBuilder.firstPageButtons(topicsCount: Int, ids: Iterable<String>) {
    if (topicsCount > PAGE_SIZE) {
        callbackData("Next >>") { "topics;page=1" }
    }
    br()
    callbackData("Refresh") { "topics;refresh" }
    br()
    callbackData("Delete") { "deleteMany=${ids.joinToString("&")}" }
}

@UnprocessedHandler
suspend fun handle(update: ProcessedUpdate) {
    println(update)
}

