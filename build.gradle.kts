plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.shadow)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.telegram.bot)

    application
}

group = "com.github.asm0dey"
version = "0.2.0"
application {
    mainClass = "BotKt"
}
repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.hoplite.toml)
    implementation(libs.tinylog.kotlin)
//    implementation(libs.tinylog.slf4j)
//    implementation(libs.tinylog.impl)
    implementation(libs.xodus.dnq)
    implementation(libs.kotlinx.serialization.json)
}

kotlin {
    jvmToolchain(21)
}