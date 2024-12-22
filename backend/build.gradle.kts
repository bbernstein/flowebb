import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

val kotlin_version: String by project
val ktor_version: String by project
val logback_version: String by project
val aws_lambda_version = "1.2.1"
val aws_lambda_events_version = "3.11.0"
val aws_sdk_version = "2.20.68"
val coroutines_version = "1.7.3"

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "com.flowebb"
version = "0.0.1"

application {
    mainClass.set("com.flowebb.lambda.TidesLambda")
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    // Core dependencies
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")
    implementation("joda-time:joda-time:2.12.5")

    // Ktor client dependencies
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-client-logging:$ktor_version")
    implementation("io.ktor:ktor-client-logging-jvm:$ktor_version")

    // AWS Lambda dependencies
    implementation("com.amazonaws:aws-lambda-java-core:$aws_lambda_version")
    implementation("com.amazonaws:aws-lambda-java-events:$aws_lambda_events_version")

    // AWS SDK dependencies
    implementation(platform("software.amazon.awssdk:bom:$aws_sdk_version"))
    implementation("software.amazon.awssdk:dynamodb") {
        exclude(group = "software.amazon.awssdk", module = "apache-client")
    }
    implementation("software.amazon.awssdk:dynamodb-enhanced") {
        exclude(group = "software.amazon.awssdk", module = "apache-client")
    }
    implementation("software.amazon.awssdk:s3") {
        exclude(group = "software.amazon.awssdk", module = "apache-client")
    }
    implementation("software.amazon.awssdk:regions")
    implementation("software.amazon.awssdk:auth")
    implementation("software.amazon.awssdk:url-connection-client:$aws_sdk_version")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Kotlin coroutines and serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.0")

    // Math
    implementation("org.apache.commons:commons-math3:3.6.1")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlin_version")
    testImplementation("io.ktor:ktor-client-mock:$ktor_version")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.mockk:mockk-agent-jvm:1.13.8")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines_version") {
        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
    }
}

tasks {

    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            apiVersion.set(KotlinVersion.KOTLIN_1_9)
            languageVersion.set(KotlinVersion.KOTLIN_1_9)
        }
    }

    compileTestKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            apiVersion.set(KotlinVersion.KOTLIN_1_9)
            languageVersion.set(KotlinVersion.KOTLIN_1_9)
        }
    }

    test {
        useJUnitPlatform()
    }

    shadowJar {
        manifest {
            attributes(
                mapOf(
                    "Implementation-Title" to "Tides API",
                    "Implementation-Version" to project.version
                )
            )
        }

        archiveBaseName.set("tides-be")
        archiveClassifier.set("")
        archiveVersion.set("")

        dependencies {
            // Core dependencies
            include(dependency("org.jetbrains.kotlin:.*"))
            include(dependency("org.jetbrains.kotlinx:.*"))
            include(dependency("joda-time:.*"))
            include(dependency("com.amazonaws:.*"))
            include(dependency("software.amazon.awssdk:.*"))
            include(dependency("org.reactivestreams:.*"))
            include(dependency("org.apache.commons:commons-math3:.*"))

            // Logging dependencies
            include(dependency("io.github.microutils:kotlin-logging-jvm"))
            include(dependency("org.slf4j:.*"))
            include(dependency("ch.qos.logback:.*"))

            // Ktor dependencies
            include(dependency("io.ktor:.*"))
        }

        mergeServiceFiles()

        minimize {
            // Keep all classes from these packages
            exclude(dependency("org.jetbrains.kotlin:.*"))
            exclude(dependency("org.jetbrains.kotlinx:.*"))
            exclude(dependency("joda-time:.*"))
            exclude(dependency("com.amazonaws:.*"))
            exclude(dependency("io.ktor:.*"))
            exclude(dependency("software.amazon.awssdk:.*"))
            exclude(dependency("ch.qos.logback:.*"))
            exclude(dependency("org.slf4j:.*"))
            exclude(dependency("io.github.microutils:.*"))
            exclude(dependency("software.amazon.awssdk:.*"))
            exclude(dependency("org.reactivestreams:.*"))
            exclude(dependency("org.apache.commons:commons-math3:.*"))
        }
    }

    build {
        dependsOn(shadowJar)
    }
}
