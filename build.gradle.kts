import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    java
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.0.0"
    id("net.ltgt.errorprone") version "5.1.0"
    id("net.ltgt.nullaway") version "3.0.0"
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.spring") version "2.3.20"
    jacoco
}

group = "dev.dnpm"
version = "0.15.8" // x-release-please-version

// Additional versions
val mtbDtoVersion by extra("0.2.0")
val hapiFhirVersion by extra("8.8.1")
val apacheCxfVersion by extra("4.1.5")
val springModulithVersion by extra("2.0.5")
val springRetryVersion by extra("2.0.12")

// Additional test versions
val mockitoKotlinVersion by extra("6.2.3")
val testcontainersVersion by extra("1.21.4")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
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
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.retry:spring-retry:${springRetryVersion}")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.flywaydb:flyway-mysql")
    implementation("commons-codec:commons-codec")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("dev.pcvolkmer.mv64e:mtb-dto:${mtbDtoVersion}")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-base:${hapiFhirVersion}")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-structures-r4:${hapiFhirVersion}")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("com.jayway.jsonpath:json-path")
    implementation("org.jspecify:jspecify")
    // gPAS via Soap
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:${apacheCxfVersion}")
    implementation("org.apache.cxf:cxf-rt-transports-http:${apacheCxfVersion}")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")

    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
    runtimeOnly("org.postgresql:postgresql")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${mockitoKotlinVersion}")

    integrationTestImplementation("org.testcontainers:junit-jupiter:${testcontainersVersion}")
    integrationTestImplementation("org.testcontainers:postgresql:${testcontainersVersion}")
    integrationTestImplementation("org.htmlunit:htmlunit")
    integrationTestImplementation("org.springframework:spring-webflux")

    errorprone("com.google.errorprone:error_prone_core:2.48.0")
    errorprone("com.uber.nullaway:nullaway:0.13.1")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:$springModulithVersion")
    }
}

tasks.withType<JavaCompile> {
    options.errorprone.nullaway {
        error()
        annotatedPackages.add("dev.dnpm.etl")
    }
    options.errorprone.disableAllChecks = true
    options.errorprone {
        disableAllWarnings = true
    }
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
    dependsOn(tasks.spotlessCheck)
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests"
    group = "verification"

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath

    shouldRunAfter("test")
}

tasks.register("allTests") {
    description = "Run all tests"
    group = JavaBasePlugin.VERIFICATION_GROUP
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
    imageName.set("ghcr.io/pcvolkmer/mv64e-etl-processor")

    // Binding for CA Certs
    bindings.set(listOf(
        "$rootDir/bindings/ca-certificates/:/platform/bindings/ca-certificates"
    ))

    environment.set(environment.get() + mapOf(
        // Enable this line to embed CA Certs into image on build time
        //"BP_EMBED_CERTS" to "true",
        "BP_OCI_SOURCE" to "https://github.com/pcvolkmer/mv64e-etl-processor",
        "BP_OCI_LICENSES" to "AGPLv3",
        "BP_OCI_DESCRIPTION" to "ETL Processor for MV § 64e and DNPM:DIP"
    ))
}

spotless {
    java {
        importOrder()
        removeUnusedImports()
        googleJavaFormat()
    }
    kotlin {
        ktlint()
        suppressLintsFor {
            step = "ktlint"
            shortCode = "standard:filename"
        }
        suppressLintsFor {
            step = "ktlint"
            shortCode = "standard:no-wildcard-imports"
        }
        leadingTabsToSpaces()
        endWithNewline()
    }
}
