import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    war
    id("org.springframework.boot") version "3.2.6"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.spring") version "1.9.23"
    jacoco
}

group = "de.ukw.ccc"
version = "0.10.0-SNAPSHOT"

var versions = mapOf(
    "bwhc-dto-java" to "0.3.0",
    "hapi-fhir" to "6.10.2",
    "httpclient5" to "5.2.1",
    "mockito-kotlin" to "5.3.1",
    "archunit" to "1.3.0",
    // Webjars
    "echarts" to "5.4.3",
    "htmx.org" to "1.9.11"
)

java {
    sourceCompatibility = JavaVersion.VERSION_21
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
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
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
    implementation("org.webjars:webjars-locator:0.52")
    implementation("org.webjars.npm:echarts:${versions["echarts"]}")
    implementation("org.webjars.npm:htmx.org:${versions["htmx.org"]}")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
    runtimeOnly("org.postgresql:postgresql")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    providedRuntime("org.springframework.boot:spring-boot-starter-tomcat")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${versions["mockito-kotlin"]}")
    integrationTestImplementation("org.testcontainers:junit-jupiter")
    integrationTestImplementation("org.testcontainers:postgresql")
    integrationTestImplementation("com.tngtech.archunit:archunit:${versions["archunit"]}")
    integrationTestImplementation("net.sourceforge.htmlunit:htmlunit")
    integrationTestImplementation("org.springframework:spring-webflux")
    // Override dependency version from org.testcontainers:junit-jupiter - CVE-2024-26308, CVE-2024-25710
    integrationTestImplementation("org.apache.commons:commons-compress:1.26.1")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
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

tasks.register("allTests") {
    dependsOn(tasks.withType<Test>())
}

tasks.jacocoTestReport {
    dependsOn("allTests")

    executionData(fileTree(project.rootDir.absolutePath).include("**/build/jacoco/*.exec"))

    reports {
        xml.required = true
    }
}

tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("ghcr.io/ccc-mf/etl-processor")

    // Binding for CA Certs
    bindings.set(listOf(
        "$rootDir/bindings/ca-certificates/:/platform/bindings/ca-certificates"
    ))

    environment.set(environment.get() + mapOf(
        // Enable this line to embed CA Certs into image on build time
        //"BP_EMBED_CERTS" to "true",
        "BP_OCI_SOURCE" to "https://github.com/CCC-MF/etl-processor",
        "BP_OCI_LICENSES" to "AGPLv3",
        "BP_OCI_DESCRIPTION" to "ETL Processor for bwHC MTB files"
    ))
}
