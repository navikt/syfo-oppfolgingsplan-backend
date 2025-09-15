
val dataFakerVersion="2.4.4"
val flyway_version="11.6.0"
val hikari_version="6.3.0"
val kafka_version="3.9.1"
val koinVersion = "4.1.1"
val kotestExtensionsVersion="2.0.0"
val kotestVersion="5.9.1"
val ktor_version="3.1.1"
val logback_version="1.4.14"
val logstashEncoderVersion="8.1"
val micrometer_version="1.14.5"
val mockkVersion="1.14.2"
val postgres_version="42.7.5"
val testcontainersVersion="1.21.3"
val valkey_version="5.3.0"

plugins {
    kotlin("jvm") version "2.2.20"
    id("io.ktor.plugin") version "3.2.3"
    id("com.gradleup.shadow") version "8.3.9"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
}

group = "no.nav.syfo"
version = "0.0.1"

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
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
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-apache-jvm")
    implementation("io.ktor:ktor-serialization-jackson")
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
    implementation("io.ktor:ktor-server-swagger")
    implementation("io.ktor:ktor-server-openapi")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("org.apache.kafka:kafka-clients:$kafka_version")
    implementation("org.apache.kafka:kafka_2.13:$kafka_version") {
        exclude(group = "log4j")
    }

    testImplementation("io.ktor:ktor-server-test-host")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktor_version")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometer_version")

    // Database
    implementation("org.postgresql:postgresql:$postgres_version")
    implementation("com.zaxxer:HikariCP:$hikari_version")
    implementation("org.flywaydb:flyway-database-postgresql:$flyway_version")

    // Caching
    implementation("io.valkey:valkey-java:$valkey_version")

    // Faker
    implementation("net.datafaker:datafaker:$dataFakerVersion")

    // Testing
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:${kotestVersion}")
    testImplementation("io.kotest:kotest-property:${kotestVersion}")
    testImplementation("io.kotest.extensions:kotest-assertions-ktor:$kotestExtensionsVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.insert-koin:koin-test:${koinVersion}")
    testImplementation("io.ktor:ktor-client-mock:${ktor_version}")
    testImplementation("io.kotest.extensions:kotest-extensions-koin:1.3.0")
    testImplementation("org.testcontainers:testcontainers:${testcontainersVersion}")
    testImplementation("org.testcontainers:postgresql:${testcontainersVersion}")
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
