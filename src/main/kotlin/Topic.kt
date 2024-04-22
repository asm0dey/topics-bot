import kotlinx.serialization.Serializable
import org.joda.time.DateTime

@Serializable
data class Topic(
    val text: String,
    val author: Long,
    val authorName: String,
    @Serializable(with = JodaDateTimeSerializer::class)
    val createdAt: DateTime
) {
    constructor(it: XdTask) : this(it.text, it.author, it.authorName, it.createdAt)
}