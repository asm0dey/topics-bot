import CleanDB.Companion.DB_DELETE_NO
import CleanDB.Companion.DB_DELETE_YES
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.annotations.RegexCommandHandler
import eu.vendeli.tgbot.annotations.UnprocessedHandler
import eu.vendeli.tgbot.api.media.sendMediaGroup
import eu.vendeli.tgbot.api.message.deleteMessages
import eu.vendeli.tgbot.api.message.editMessageText
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.ParseMode.Markdown
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.internal.*
import eu.vendeli.tgbot.types.media.InputMedia
import eu.vendeli.tgbot.utils.builders.InlineKeyboardMarkupBuilder
import eu.vendeli.tgbot.utils.setChain
import jetbrains.exodus.database.TransientEntityStore
import jetbrains.exodus.entitystore.Entity
import kotlinx.coroutines.delay
import kotlinx.dnq.*
import kotlinx.dnq.query.*
import kotlinx.dnq.store.container.StaticStoreContainer
import kotlinx.dnq.util.initMetaData
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveKind.STRING
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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

class XdTask(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdTask>()

    var text by xdRequiredStringProp(unique = true, trimmed = true)
    var author by xdRequiredLongProp()
    var createdAt by xdRequiredDateTimeProp()
    var finishedAt by xdDateTimeProp()
    var authorName by xdRequiredStringProp()
    var chatId by xdRequiredLongProp()
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


@RegexCommandHandler("/addtopic ?.*")
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

private const val TOPIC_PREFIX = "topics;"
private const val PAGE_SIZE = 9

@CommandHandler(["/topics"])
suspend fun topics(bot: TelegramBot, upd: MessageUpdate) {
    val topicsCount = countTopicsInChat(upd.message.chat.id)
    val (ids, text) = store.transactional {
        val toList = chatTasks(upd.message.chat.id)
            .take(PAGE_SIZE)
            .toList()
        toList.map { it.xdId } to toList.textTopics()
    }

    message(text)
        .options { parseMode = Markdown }
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
        .joinToString("\n", postfix = "\n---\nPage ${page + 1}")

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
    message { "`${user.id}`" }.options { parseMode = Markdown }.send(user, bot)
}

@RegexCommandHandler(value = "$TOPIC_PREFIX.*")
suspend fun updateTopicsMessage(bot: TelegramBot, up: CallbackQueryUpdate) {
    val chatId = up.callbackQuery.message?.chat?.id ?: return
    val topicsCount = countTopicsInChat(chatId)
    val messageId = up.callbackQuery.message?.messageId
    val pagePrefix = "${TOPIC_PREFIX}page="
    if (up.text.startsWith(pagePrefix)) {
        val page = up.text.substringAfter(pagePrefix).toInt()
        val (ids, topics) = store.transactional {
            val q = chatTasks(chatId).drop(PAGE_SIZE * page).take(PAGE_SIZE)
            q.asIterable().map { it.xdId } to q.asIterable().textTopics(page)
        }

        editMessageText(messageId ?: return) { topics }
            .options { parseMode = Markdown }
            .inlineKeyboardMarkup {
                if (page != 0)
                    callbackData("<< Prev") { "${TOPIC_PREFIX}page=${page - 1}" }
                if (PAGE_SIZE * page + PAGE_SIZE < topicsCount)
                    callbackData("Next >>") { "${TOPIC_PREFIX}page=${page + 1}" }
                if (page != 0 || PAGE_SIZE * page + PAGE_SIZE < topicsCount)
                    br()
                callbackData("Refresh") { "${TOPIC_PREFIX}refresh" }
                br()
                callbackData("Delete") { "deleteMany=${ids.joinToString("&")}" }
            }.send(chatId, bot)
    } else if (up.text == "${TOPIC_PREFIX}refresh") {
        val (ids, text) = store.transactional {
            val tasks = chatTasks(chatId)
                .take(PAGE_SIZE)
                .asIterable()
            tasks.map { it.xdId } to tasks.textTopics()
        }
        editMessageText(messageId ?: return) { text }
            .options { parseMode = Markdown }
            .inlineKeyboardMarkup { firstPageButtons(topicsCount, ids) }
            .send(chatId, bot)
    }
}

@RegexCommandHandler("deleteMany=.*")
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

@CommandHandler(["export"])
suspend fun export(bot: TelegramBot, up: MessageUpdate) {
    val chat = up.message.chat
    val allTasks = store.transactional {
        chatTasks(chat.id).toList().map { Task(it) }
    }
    val data = Json.encodeToString(allTasks)
    sendMediaGroup(
        InputMedia.Document(
            ImplicitFile.InpFile(
                InputFile(
                    data.toByteArray(UTF_8),
                    LocalDateTime.now().toString(),
                    "application/json"
                )
            )
        )
    ).send(chat, bot)
}

@Serializable
data class Task(
    val text: String,
    val author: Long,
    val authorName: String,
    @Serializable(with = JodaDateTimeSerializer::class)
    val createdAt: DateTime
) {
    constructor(it: XdTask) : this(it.text, it.author, it.authorName, it.createdAt)
}

class JodaDateTimeSerializer : KSerializer<DateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DateTime", STRING)

    override fun deserialize(decoder: Decoder): DateTime = DateTime.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: DateTime) {
        encoder.encodeString(value.toString())
    }

}

@RegexCommandHandler("delete=.*")
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

private fun chatTasks(chatId: Long) = XdTask
    .filter { it.chatId eq chatId and (it.finishedAt eq null) }
    .sortedBy(XdTask::createdAt, asc = false)

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

