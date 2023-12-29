import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    war
    id("org.springframework.boot") version "3.2.1"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.spring") version "1.9.21"
}

group = "de.ukw.ccc"
version = "0.3.0"

var versions = mapOf(
    "bwhc-dto-java" to "0.2.0",
    "hapi-fhir" to "6.10.2",
    "httpclient5" to "5.2.1",
    "mockito-kotlin" to "5.1.0"
)

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
    extendsFrom(configurations.runtimeOnly.get())
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    maven {
        url = uri("https://git.dnpm.dev/api/packages/public/maven")
    }
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-mysql")
    implementation("commons-codec:commons-codec")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("de.ukw.ccc:bwhc-dto-java:${versions["bwhc-dto-java"]}")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-base:${versions["hapi-fhir"]}")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-structures-r4:${versions["hapi-fhir"]}")
    implementation("org.apache.httpcomponents.client5:httpclient5:${versions["httpclient5"]}")
    implementation("com.jayway.jsonpath:json-path")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
    runtimeOnly("org.postgresql:postgresql")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    providedRuntime("org.springframework.boot:spring-boot-starter-tomcat")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${versions["mockito-kotlin"]}")
    integrationTestImplementation("org.testcontainers:junit-jupiter")
    integrationTestImplementation("org.testcontainers:postgresql")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
    }
}

task<Test>("integrationTest") {
    description = "Runs integration tests"

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath

    shouldRunAfter("test")
}

tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("ghcr.io/ccc-mf/etl-processor")

    environment.set(environment.get() + mapOf(
        "BP_OCI_SOURCE" to "https://github.com/CCC-MF/etl-processor",
        "BP_OCI_LICENSES" to "AGPLv3",
        "BP_OCI_DESCRIPTION" to "ETL Processor for bwHC MTB files"
    ))
}
