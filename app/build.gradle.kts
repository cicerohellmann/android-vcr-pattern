import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageOutputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hellmannratti.vcr"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.hellmannratti.vcr"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Step 1: Networking & JSON dependencies
    implementation(libs.okhttp)
    // JSON: Kotlinx Serialization instead of Moshi
    implementation(libs.kotlinx.serialization.json)

    implementation(project(":cassete-core"))
    implementation(project(":cassete-okhttp"))
    // Image loading for Compose
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.okhttp.mockwebserver)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

fun resolveAdbExecutable(): String {
    val sdkRoot = System.getenv("ANDROID_SDK_ROOT")
        ?: System.getenv("ANDROID_HOME")
        ?: error("Set ANDROID_SDK_ROOT or ANDROID_HOME before running visual proof tasks.")
    return File(sdkRoot, "platform-tools/adb").absolutePath
}

fun File.ensureParent(): File {
    parentFile?.mkdirs()
    return this
}

fun gifWriteParams(writer: ImageWriter): ImageWriteParam = writer.defaultWriteParam

fun gifFrameMetadata(
    writer: ImageWriter,
    image: BufferedImage,
    delayMs: Int
) = writer.getDefaultImageMetadata(
    javax.imageio.ImageTypeSpecifier.createFromRenderedImage(image),
    gifWriteParams(writer)
).apply {
    val format = nativeMetadataFormatName
    val root = getAsTree(format) as IIOMetadataNode
    val graphicsControl =
        root.getElementsByTagName("GraphicControlExtension").item(0) as IIOMetadataNode
    graphicsControl.setAttribute("disposalMethod", "none")
    graphicsControl.setAttribute("userInputFlag", "FALSE")
    graphicsControl.setAttribute("transparentColorFlag", "FALSE")
    graphicsControl.setAttribute("delayTime", (delayMs / 10).coerceAtLeast(1).toString())
    graphicsControl.setAttribute("transparentColorIndex", "0")

    val appExtensions = IIOMetadataNode("ApplicationExtensions")
    val appNode = IIOMetadataNode("ApplicationExtension")
    appNode.setAttribute("applicationID", "NETSCAPE")
    appNode.setAttribute("authenticationCode", "2.0")
    appNode.userObject = byteArrayOf(0x1, 0x0, 0x0)
    appExtensions.appendChild(appNode)
    root.appendChild(appExtensions)
    setFromTree(format, root)
}

fun writeAnimatedGif(
    outputFile: File,
    frames: List<File>,
    delayMs: Int
) {
    require(frames.isNotEmpty()) { "frames must not be empty" }
    val writer = ImageIO.getImageWritersBySuffix("gif").asSequence().firstOrNull()
        ?: error("No GIF writer available in the current JVM image.")

    FileImageOutputStream(outputFile.ensureParent()).use { output ->
        writer.output = output
        writer.prepareWriteSequence(null)
        frames.forEach { frame ->
            val image = ImageIO.read(frame) ?: error("Failed to read frame ${frame.absolutePath}")
            writer.writeToSequence(
                IIOImage(image, null, gifFrameMetadata(writer = writer, image = image, delayMs = delayMs)),
                gifWriteParams(writer)
            )
        }
        writer.endWriteSequence()
    }
}

val clearDeviceVisualProof by tasks.registering {
    group = "verification"
    description = "Clears prior sample-app visual proof artifacts from the connected device."
    doLast {
        exec {
            commandLine(
                resolveAdbExecutable(),
                "shell",
                "rm",
                "-rf",
                "/storage/emulated/0/Download/cassete_visual_proof"
            )
        }
    }
}

val runSampleAppVisualProofOnDevice by tasks.registering {
    group = "verification"
    description = "Runs only the visual-proof instrumented scenario on the connected device."
    dependsOn(clearDeviceVisualProof)
    dependsOn("installDebug")
    dependsOn("installDebugAndroidTest")

    doLast {
        exec {
            commandLine(
                resolveAdbExecutable(),
                "shell",
                "am",
                "force-stop",
                "com.hellmannratti.vcr"
            )
        }
        exec {
            commandLine(
                resolveAdbExecutable(),
                "shell",
                "am",
                "instrument",
                "-w",
                "-e",
                "class",
                "com.hellmannratti.vcr.CasseteVisualProofInstrumentedTest",
                "com.hellmannratti.vcr.test/androidx.test.runner.AndroidJUnitRunner"
            )
        }
    }
}

val pullSampleAppVisualProof by tasks.registering {
    group = "verification"
    description = "Pulls sample-app visual proof artifacts from the device into build reports."
    dependsOn(runSampleAppVisualProofOnDevice)

    doLast {
        val reportsRoot = layout.buildDirectory.dir("reports/visual-proof").get().asFile
        val rawRoot = File(reportsRoot, "raw")
        delete(rawRoot)
        rawRoot.mkdirs()

        exec {
            commandLine(
                resolveAdbExecutable(),
                "pull",
                "/storage/emulated/0/Download/cassete_visual_proof",
                rawRoot.absolutePath
            )
        }
    }
}

val packageSampleAppVisualProof by tasks.registering {
    group = "verification"
    description = "Packages sample-app PNG and GIF proof artifacts into a stable reports bundle."
    dependsOn(pullSampleAppVisualProof)

    doLast {
        val reportsRoot = layout.buildDirectory.dir("reports/visual-proof").get().asFile
        val pulledRoot = File(reportsRoot, "raw/cassete_visual_proof")
        require(pulledRoot.exists()) {
            "Missing pulled visual proof artifacts at ${pulledRoot.absolutePath}"
        }

        val assetsDir = File(reportsRoot, "assets")
        delete(assetsDir)
        assetsDir.mkdirs()

        File(pulledRoot, "static")
            .listFiles()
            ?.filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                file.copyTo(File(assetsDir, file.name), overwrite = true)
            }

        val replayFrames = File(pulledRoot, "motion/github_record_to_replay")
            .listFiles()
            ?.filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()

        if (replayFrames.isNotEmpty()) {
            writeAnimatedGif(
                outputFile = File(assetsDir, "github_record_to_replay.gif"),
                frames = replayFrames,
                delayMs = 180
            )
        }

        File(reportsRoot, "README.md").writeText(
            """
            # Sample App Visual Proof

            This bundle contains the sample-app visual proof artifacts generated from the deterministic GitHub record-to-replay flow.

            ## Assets

            - `sample_app_record_home.png`
            - `sample_app_record_response.png`
            - `sample_app_replay_home.png`
            - `sample_app_replay_response.png`
            - `github_record_to_replay.gif`

            ## Source

            - Instrumented source: `app/src/androidTest/java/com/hellmannratti/vcr/CasseteVisualProofInstrumentedTest.kt`
            - Packaging task: `:app:sampleAppVisualProof`
            """.trimIndent()
        )
    }
}

tasks.register("sampleAppVisualProof") {
    group = "verification"
    description = "Runs the sample-app visual proof flow and packages static PNG plus GIF evidence."
    dependsOn(clearDeviceVisualProof)
    dependsOn(packageSampleAppVisualProof)
}
