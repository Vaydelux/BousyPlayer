plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main { kotlin.srcDir("../native-smoke") }
    }
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    val engine = "../../app/src/main/java/com/vaydelux/bousyplayer/engine"
    source(file("$engine/PlaybackPolicy.kt"))
    source(file("$engine/PlaybackEngine.kt"))
    source(file("$engine/MpvEngine.kt"))
    source(file("$engine/VlcEngine.kt"))
}

application {
    mainClass.set("NativeSmokeKt")
}
