plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main { kotlin.srcDir("../session-smoke") }
    }
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    source(file("../../app/src/main/java/com/vaydelux/bousyplayer/engine/PlaybackPolicy.kt"))
    source(file("../../app/src/main/java/com/vaydelux/bousyplayer/engine/PlayerSession.kt"))
}

application {
    mainClass.set("SessionSmokeKt")
}
