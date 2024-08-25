import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.InputChain
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.generated.userData
import eu.vendeli.tgbot.types.User
import eu.vendeli.tgbot.types.internal.BreakCondition
import eu.vendeli.tgbot.types.internal.ChainLink
import eu.vendeli.tgbot.types.internal.ProcessedUpdate
import kotlinx.dnq.query.toList

@InputChain
class CleanDB {
    companion object {
        const val DB_DELETE_YES = "yes"
        const val DB_DELETE_NO = "NO"
    }

    object Try : CleanDbLink() {
        override val breakCondition = BreakCondition { _, update, _ -> update.text != DB_DELETE_YES }
        override val retryAfterBreak: Boolean = false

        override suspend fun action(user: User, update: ProcessedUpdate, bot: TelegramBot): Long? {
            val to = state.get(user) ?: return null
            store.transactional {
                XdTask.all().toList().forEach {
                    it.delete()
                }
            }
            message { "Okay boss. Gotcha" }
                .replyKeyboardRemove(false)
                .send(to, bot)
            state.del(user)
            return null
        }

        override suspend fun breakAction(user: User, update: ProcessedUpdate, bot: TelegramBot) {
            message { "ABORT! I REPEAT ABORT!" }
                .replyKeyboardRemove(false)
                .send(state.get(user) ?: return, bot)
            state.del(user)
        }
    }
}