plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.shadow)
    application
}

group = "com.github.asm0dey"
version = "0.1.0"
application {
    mainClass = "BotKt"
}
repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.telegram.bot)
    ksp(libs.telegram.ksp)
    implementation(libs.hoplite.toml)
    implementation(libs.tinylog.kotlin)
    implementation(libs.tinylog.slf4j)
    implementation(libs.tinylog.impl)
    implementation(libs.xodus.dnq)
}

kotlin {
    jvmToolchain(21)
}