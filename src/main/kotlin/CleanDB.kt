import eu.vendeli.tgbot.annotations.WizardHandler
import eu.vendeli.tgbot.api.message.message
import eu.vendeli.tgbot.types.chain.Transition
import eu.vendeli.tgbot.types.chain.WizardContext
import eu.vendeli.tgbot.types.chain.WizardStep
import eu.vendeli.tgbot.types.component.getChat
import kotlinx.dnq.query.toList

@WizardHandler(trigger = ["/cleandb"])
object CleanDbWizard {
    object Confirm : WizardStep(isInitial = true) {
        override suspend fun onEntry(ctx: WizardContext) {
            val chatId = ctx.update.getChat().id
            if (ctx.user.id != config.bot.admin) {
                message { "Only the bot owner can do it, bro" }.send(chatId, ctx.bot)
                return
            }
            message { "Sure?" }.inlineKeyboardMarkup {
                callbackData("yes") { "yes" }
                callbackData("NO") { "NO" }
            }.send(chatId, ctx.bot)
        }

        override suspend fun validate(ctx: WizardContext): Transition {
            val chatId = ctx.update.getChat().id
            // ponytail: non-admin who reached validate just ends the wizard silently
            if (ctx.user.id != config.bot.admin) return Transition.Finish
            if (ctx.update.text == "yes") {
                store.transactional { XdTask.all().toList().forEach { it.delete() } }
                message { "Okay boss. Gotcha" }.replyKeyboardRemove(false).send(chatId, ctx.bot)
            } else {
                message { "ABORT! I REPEAT ABORT!" }.replyKeyboardRemove(false).send(chatId, ctx.bot)
            }
            return Transition.Finish
        }
    }
}
