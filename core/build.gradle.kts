import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit)
}

// ---------------------------------------------------------------------------
// Config code generation
// Reads config/coinrain.json and emits CoinRainConfig.kt into the build dir.
// ---------------------------------------------------------------------------

val generateConfig by tasks.registering {
    notCompatibleWithConfigurationCache("reads project file in doLast")
    val inputFile = rootProject.file("config/coinrain.json")
    val outputDir = layout.buildDirectory.dir("generated/coinrain/com/example/coinrain/core")
    inputs.file(inputFile)
    outputs.dir(outputDir)

    doLast {
        @Suppress("UNCHECKED_CAST")
        val json = JsonSlurper().parse(inputFile) as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val physics = json["physics"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val sound   = json["sound"]   as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val rendering = json["rendering"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val coins   = json["coins"]   as List<Map<String, Any>>

        fun Number.f() = "${toDouble()}f"
        fun Map<String, Any>.f(key: String) = (get(key) as Number).f()
        fun Map<String, Any>.i(key: String) = (get(key) as Number).toInt()
        fun Map<String, Any>.b(key: String, default: Boolean = false) =
            get(key) as? Boolean ?: default
        fun Map<String, Any>.s(key: String, default: String = "") =
            get(key) as? String ?: default

        fun hexToArgb(hex: String): String {
            val clean = hex.trimStart('#')
            val argb = "FF$clean".uppercase()
            return "0x$argb.toInt()"
        }

        val coinLines = coins.joinToString(",\n        ") { c ->
            val bimetallic = c.b("bimetallic")
            buildString {
                append("CoinSpec(\n")
                append("            id = \"${c.s("id")}\",\n")
                append("            label = \"${c.s("label")}\",\n")
                append("            diameterMm = ${c.f("diameterMm")},\n")
                append("            weightG = ${c.f("weightG")},\n")
                append("            color = ${hexToArgb(c.s("color"))},\n")
                append("            rimColor = ${hexToArgb(c.s("rimColor"))},\n")
                if (bimetallic) append("            centerColor = ${hexToArgb(c.s("centerColor", "#000000"))},\n")
                append("            glossAlpha = ${c.f("glossAlpha")},\n")
                append("            bimetallic = $bimetallic,\n")
                append("            soundFreqHz = ${c.f("soundFreqHz")},\n")
                append("            soundDecayMs = ${c.f("soundDecayMs")}\n")
                append("        )")
            }
        }

        val src = buildString {
            appendLine("package com.example.coinrain.core")
            appendLine()
            appendLine("// AUTO-GENERATED — do not edit. Source: config/coinrain.json")
            appendLine()
            appendLine("object CoinRainConfig {")
            appendLine("    object Physics {")
            appendLine("        const val SIM_HZ: Int = ${physics.i("simHz")}")
            appendLine("        const val RESTITUTION: Float = ${physics.f("restitution")}")
            appendLine("        const val RESTITUTION_SLOP_VELOCITY: Float = ${physics.f("restitutionSlopVelocity")}")
            appendLine("        const val FRICTION: Float = ${physics.f("friction")}")
            appendLine("        const val AIR_DRAG_COEFF: Float = ${physics.f("airDragCoeff")}")
            appendLine("        const val SLEEP_VELOCITY_THRESHOLD: Float = ${physics.f("sleepVelocityThreshold")}")
            appendLine("        const val SLEEP_ACCEL_THRESHOLD: Float = ${physics.f("sleepAccelThreshold")}")
            appendLine("        const val SLEEP_FRAMES: Int = ${physics.i("sleepFrames")}")
            appendLine("        const val WAKE_SHAKE_THRESHOLD: Float = ${physics.f("wakeShakeThreshold")}")
            appendLine("        const val POSITION_CORRECTION_SLOP: Float = ${physics.f("positionCorrectionSlop")}")
            appendLine("        const val POSITION_SOLVER_ITERATIONS: Int = ${physics.i("positionSolverIterations")}")
            appendLine("    }")
            appendLine("    object Sound {")
            appendLine("        const val ENERGY_THRESHOLD: Float = ${sound.f("energyThreshold")}")
            appendLine("        const val PITCH_VARIATION: Float = ${sound.f("pitchVariation")}")
            appendLine("        const val DECAY_VARIATION: Float = ${sound.f("decayVariation")}")
            appendLine("        const val MIXER_MAX_STREAMS: Int = ${sound.i("mixerMaxStreams")}")
            appendLine("        const val NOISE_FILTER_LOW_HZ: Float = ${sound.f("noiseFilterLowHz")}")
            appendLine("        const val NOISE_FILTER_HIGH_HZ: Float = ${sound.f("noiseFilterHighHz")}")
            appendLine("        const val NOISE_DURATION_MS: Float = ${sound.f("noiseDurationMs")}")
            appendLine("    }")
            appendLine("    object Rendering {")
            appendLine("        const val RIM_RIDGE_COUNT: Int = ${rendering.i("rimRidgeCount")}")
            appendLine("        const val STAR_COUNT: Int = ${rendering.i("starCount")}")
            appendLine("        const val STAR_RADIUS_RATIO: Float = ${rendering.f("starRadiusRatio")}")
            appendLine("    }")
            appendLine("    val COINS: List<CoinSpec> = listOf(")
            appendLine("        $coinLines")
            appendLine("    )")
            appendLine("}")
            appendLine()
            appendLine("data class CoinSpec(")
            appendLine("    val id: String,")
            appendLine("    val label: String,")
            appendLine("    val diameterMm: Float,")
            appendLine("    val weightG: Float,")
            appendLine("    val color: Int,")
            appendLine("    val rimColor: Int,")
            appendLine("    val centerColor: Int = 0,")
            appendLine("    val glossAlpha: Float = 0.35f,")
            appendLine("    val bimetallic: Boolean = false,")
            appendLine("    val soundFreqHz: Float,")
            appendLine("    val soundDecayMs: Float")
            appendLine(")")
        }

        val outDir = outputDir.get().asFile
        outDir.mkdirs()
        File(outDir, "CoinRainConfig.kt").writeText(src)
        println("Generated CoinRainConfig.kt")
    }
}

kotlin.sourceSets["main"].kotlin.srcDir(
    generateConfig.map { layout.buildDirectory.dir("generated/coinrain").get().asFile }
)

tasks.named("compileKotlin") { dependsOn(generateConfig) }

// ---------------------------------------------------------------------------
// Sound preview CLI task
// ---------------------------------------------------------------------------

tasks.register<JavaExec>("runSoundPreview") {
    group = "verification"
    description = "Generates WAV files for all coin denominations to build/sound-preview/"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.coinrain.core.SoundPreviewKt")
    args = listOf(layout.buildDirectory.dir("sound-preview").get().asFile.absolutePath)
    dependsOn("classes")
}
