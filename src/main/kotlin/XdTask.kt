import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*

class XdTask(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdTask>()

    var text by xdRequiredStringProp(unique = true, trimmed = true)
    var author by xdRequiredLongProp()
    var createdAt by xdRequiredDateTimeProp()
    var finishedAt by xdDateTimeProp()
    var authorName by xdRequiredStringProp()
    var chatId by xdRequiredLongProp()
}
