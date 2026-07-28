import groovy.json.JsonSlurper
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

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
        val haptics = json["haptics"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val coins   = json["coins"]   as List<Map<String, Any>>
        @Suppress("UNCHECKED_CAST")
        val defaultCoins = json["defaultCoins"] as List<String>

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
                append("            bimetallic = $bimetallic\n")
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
            appendLine("        const val ENABLED: Boolean = ${sound.b("enabled", true)}")
            appendLine("        const val MASTER_VOLUME: Float = ${sound.f("masterVolume")}")
            appendLine("        const val MIN_IMPACT_VELOCITY_FOR_SOUND: Float = ${sound.f("minImpactVelocityForSound")}")
            appendLine("    }")
            appendLine("    object Rendering {")
            appendLine("        const val RENDERER: String = \"${rendering.s("renderer", "procedural")}\"")
            appendLine("        const val RIM_RIDGE_COUNT: Int = ${rendering.i("rimRidgeCount")}")
            appendLine("        const val STAR_COUNT: Int = ${rendering.i("starCount")}")
            appendLine("        const val STAR_RADIUS_RATIO: Float = ${rendering.f("starRadiusRatio")}")
            appendLine("    }")
            val blockList = ((haptics["modelBlocklist"] as? List<*>) ?: emptyList<Any>())
                .filterIsInstance<String>().joinToString(", ") { "\"$it\"" }
            val allowList = ((haptics["modelAllowlist"] as? List<*>) ?: emptyList<Any>())
                .filterIsInstance<String>().joinToString(", ") { "\"$it\"" }
            appendLine("    object Haptics {")
            appendLine("        const val MODE: String = \"${haptics.s("mode", "auto")}\"")
            appendLine("        const val SCALE_WITH_IMPACT: Boolean = ${haptics.b("scaleWithImpact", true)}")
            appendLine("        val MODEL_BLOCKLIST: List<String> = listOf($blockList)")
            appendLine("        val MODEL_ALLOWLIST: List<String> = listOf($allowList)")
            appendLine("    }")
            appendLine("    val COINS: List<CoinSpec> = listOf(")
            appendLine("        $coinLines")
            appendLine("    )")
            val defaultList = defaultCoins.joinToString(", ") { "\"$it\"" }
            appendLine("    val DEFAULT_COINS: List<String> = listOf($defaultList)")
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
            appendLine("    val bimetallic: Boolean = false")
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
// Placeholder PNG coin asset generation
// Produces 256×256 ARGB PNGs in coinrain/src/main/res/drawable-nodpi/ using
// the host JVM's AWT (no Android runtime needed).
//
// Run once:  ./gradlew :core:generateCoinAssets
// Replace the output files later with real assets (same names, same dir).
// ---------------------------------------------------------------------------

val generateCoinAssets by tasks.registering {
    description = "Generates placeholder 256×256 coin PNG assets via AWT."
    val outputDir = rootProject.file("coinrain/src/main/res/drawable-nodpi")
    outputs.dir(outputDir)

    doLast {
        System.setProperty("java.awt.headless", "true")
        outputDir.mkdirs()

        data class CoinDef(
            val id: String, val label: String,
            val rgb: Int, val rimRgb: Int,
            val centerRgb: Int = 0, val bimetallic: Boolean = false,
            val glossAlpha: Int = 89
        )

        val coins = listOf(
            CoinDef("cent_1",  "1 ct",  0xB87333, 0x8B5E3C),
            CoinDef("cent_2",  "2 ct",  0xB87333, 0x8B5E3C),
            CoinDef("cent_5",  "5 ct",  0xB87333, 0x8B5E3C),
            CoinDef("cent_10", "10 ct", 0xC8963E, 0xA0762E, glossAlpha = 102),
            CoinDef("cent_20", "20 ct", 0xC8963E, 0xA0762E, glossAlpha = 102),
            CoinDef("cent_50", "50 ct", 0xC8963E, 0xA0762E, glossAlpha = 102),
            CoinDef("euro_1",  "1 €",   0xC0C0C0, 0xA0A0A0, 0xC8963E, true, 115),
            CoinDef("euro_2",  "2 €",   0xC8963E, 0xA0762E, 0xC0C0C0, true, 115),
        )

        val size = 256
        val cx = size / 2.0
        val cy = size / 2.0
        val radius = size / 2.0 - 2.0

        fun rgb(v: Int, alpha: Int = 255) =
            Color((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF, alpha)

        for (coin in coins) {
            val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY)

            // Base disk
            g.color = rgb(coin.rgb)
            g.fill(Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2))

            // Bimetallic center
            if (coin.bimetallic) {
                val cr = radius * 0.55
                g.color = rgb(coin.centerRgb)
                g.fill(Ellipse2D.Double(cx - cr, cy - cr, cr * 2, cr * 2))
            }

            // Rim ridges
            val ridgeR = radius * 0.92
            val ridgeStep = 2 * Math.PI / 24
            g.color = rgb(coin.rimRgb)
            g.stroke = BasicStroke((radius * 0.07).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (i in 0 until 24) {
                if (i % 2 == 0) {
                    val startDeg = -Math.toDegrees(i * ridgeStep)
                    val extDeg   = -Math.toDegrees(ridgeStep * 0.6)
                    g.draw(Arc2D.Double(cx - ridgeR, cy - ridgeR, ridgeR * 2, ridgeR * 2, startDeg, extDeg, Arc2D.OPEN))
                }
            }

            // EU stars (bimetallic only)
            if (coin.bimetallic) {
                g.color = Color(255, 215, 0, 200)
                g.stroke = BasicStroke(1f)
                val starR = radius * 0.72
                val step  = 2 * Math.PI / 12
                for (i in 0 until 12) {
                    val a = i * step - Math.PI / 2
                    val sx = cx + starR * Math.cos(a)
                    val sy = cy + starR * Math.sin(a)
                    val sr = radius * 0.05
                    g.fill(Ellipse2D.Double(sx - sr, sy - sr, sr * 2, sr * 2))
                }
            }

            // Radial gloss gradient
            g.paint = RadialGradientPaint(
                Point2D.Double(cx - radius * 0.3, cy - radius * 0.3),
                (radius * 0.9).toFloat(),
                floatArrayOf(0f, 1f),
                arrayOf(Color(255, 255, 255, coin.glossAlpha), Color(255, 255, 255, 0))
            )
            g.fill(Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2))

            // Rim outline
            g.paint = null
            g.color = Color(0, 0, 0, 120)
            g.stroke = BasicStroke((radius * 0.04).toFloat())
            val inset = radius * 0.02
            g.draw(Ellipse2D.Double(cx - radius + inset, cy - radius + inset, (radius - inset) * 2, (radius - inset) * 2))

            // Denomination label
            g.color = Color(40, 20, 0, 200)
            val fontSize = (radius * 0.38).toFloat().toInt().coerceAtLeast(10)
            g.font = Font(Font.SANS_SERIF, Font.BOLD, fontSize)
            val fm = g.fontMetrics
            val tw = fm.stringWidth(coin.label)
            g.drawString(coin.label, (cx - tw / 2).toFloat(), (cy + fm.ascent * 0.35).toFloat())

            g.dispose()

            val outFile = File(outputDir, "coin_${coin.id}.png")
            ImageIO.write(img, "PNG", outFile)
            println("  Generated ${outFile.name}")
        }
        println("Done — ${coins.size} assets written to $outputDir")
    }
}

