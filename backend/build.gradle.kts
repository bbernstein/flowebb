import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

val kotlin_version: String by project
val ktor_version: String by project
val logback_version: String by project
val aws_lambda_version = "1.2.1"
val aws_lambda_events_version = "3.11.0"
val aws_sdk_version = "2.20.68"

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
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Kotlin coroutines and serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlin_version")
    testImplementation("io.ktor:ktor-client-mock:$ktor_version")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.mockk:mockk-agent-jvm:1.13.8")
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

        mergeServiceFiles()

        // Relocate AWS SDK classes to avoid conflicts
        relocate("software.amazon.awssdk", "shadow.software.amazon.awssdk")

        // Minimize the JAR but exclude necessary dependencies
        minimize {
            exclude(dependency("io.ktor:.*:.*"))
            exclude(dependency("software.amazon.awssdk:.*:.*"))
            exclude(dependency("org.jetbrains.kotlinx:.*:.*"))
            exclude(dependency("ch.qos.logback:.*:.*"))
        }
    }

    build {
        dependsOn(shadowJar)
    }
}
