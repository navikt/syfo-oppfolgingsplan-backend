val koin_version: String by project
val kotlin_version: String by project
val ktor_version: String by project
val micrometer_version: String by project
val logback_version: String by project
val postgres_vesion: String by project
val flyway_version: String by project
val hikari_version: String by project

plugins {
    kotlin("jvm") version "2.1.20"
    id("io.ktor.plugin") version "3.1.3"
    id("com.gradleup.shadow") version "8.3.6"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20"
}

group = "no.nav.syfo"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("io.ktor:ktor-server-csrf")
    implementation("io.ktor:ktor-server-request-validation")
    implementation("io.ktor:ktor-server-resources")
    implementation("io.ktor:ktor-server-host-common")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-call-id")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-jackson")
    implementation("io.insert-koin:koin-ktor:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    testImplementation("io.ktor:ktor-server-test-host")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktor_version")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometer_version")

    // Database
    implementation("org.postgresql:postgresql:$postgres_vesion")
    implementation("com.zaxxer:HikariCP:$hikari_version")
    implementation("org.flywaydb:flyway-database-postgresql:$flyway_version")
}

application {
    mainClass.set("no.nav.syfo.AppKt")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    jar {
        manifest.attributes["Main-Class"] = "no.nav.syfo.AppKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    shadowJar {
        mergeServiceFiles()
        archiveFileName.set("app.jar")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    test {
        useJUnitPlatform()
    }
}
