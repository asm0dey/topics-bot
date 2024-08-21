import eu.vendeli.tgbot.types.internal.IdLong
import eu.vendeli.tgbot.types.internal.chain.BaseLinkStateManager
import eu.vendeli.tgbot.types.internal.chain.LinkStateManager
import eu.vendeli.tgbot.types.internal.chain.StatefulLink

abstract class TopicsLink : StatefulLink<IdLong, List<Topic>>() {
    override val state: LinkStateManager<IdLong, List<Topic>> = BaseLinkStateManager { it.origin.message?.chat }
}
