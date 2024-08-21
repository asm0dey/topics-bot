import eu.vendeli.tgbot.types.internal.IdLong
import eu.vendeli.tgbot.types.internal.chain.BaseLinkStateManager
import eu.vendeli.tgbot.types.internal.chain.LinkStateManager
import eu.vendeli.tgbot.types.internal.chain.StatefulLink

abstract class CleanDbLink : StatefulLink<IdLong, Long?>() {
    override val state: LinkStateManager<IdLong, Long?> = BaseLinkStateManager()
}