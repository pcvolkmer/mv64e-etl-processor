package dev.dnpm.etl.processor

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.repository.Repository

class EtlProcessorArchTest {
    private lateinit var noTestClasses: JavaClasses

    @BeforeEach
    fun setUp() {
        this.noTestClasses =
            ClassFileImporter()
                .withImportOption { !(it.contains("/test/") || it.contains("/integrationTest/")) }
                .importPackages("dev.dnpm.etl.processor")
    }

    @Test
    fun noClassesInInputPackageShouldDependOnMonitoringPackage() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..input")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..monitoring")

        rule.check(noTestClasses)
    }

    @Test
    fun noClassesInInputPackageShouldDependOnRepositories() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..input")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")

        rule.check(noTestClasses)
    }

    @Test
    fun noClassesInOutputPackageShouldDependOnRepositories() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..output")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")

        rule.check(noTestClasses)
    }

    @Test
    fun noClassesInWebPackageShouldDependOnRepositories() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..web")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")

        rule.check(noTestClasses)
    }

    @Test
    fun repositoryClassNamesShouldEndWithRepository() {
        val rule =
            classes()
                .that()
                .areInterfaces()
                .and()
                .areAssignableTo(Repository::class.java)
                .should()
                .haveSimpleNameEndingWith("Repository")

        rule.check(noTestClasses)
    }
}
