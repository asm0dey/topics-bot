import com.sksamuel.hoplite.*
import java.io.File

data class Bot(val token: Masked, val admin: Long)
data class Database(val location: File)
data class Config(val bot: Bot, val database: Database)

val config by lazy {
    ConfigLoaderBuilder
        .default()
        .addEnvironmentSource()
        .addFileSource(File(System.getenv("CONFIG_FILE") ?: "config.toml"), optional = true)
        .build()
        .loadConfigOrThrow<Config>()
}