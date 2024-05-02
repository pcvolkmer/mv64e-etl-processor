package dev.dnpm.etl.processor

import com.tngtech.archunit.base.DescribedPredicate.doNot
import com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.conditions.ArchPredicates.have
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.repository.Repository

class EtlProcessorArchTest {

    private lateinit var noTestClasses: JavaClasses

    @BeforeEach
    fun setUp() {
        this.noTestClasses = ClassFileImporter().importPackages("dev.dnpm.etl.processor")
            .that(doNot(have(simpleNameEndingWith("Test").or(simpleNameEndingWith("Tests")))))
    }

    @Test
    fun noClassesInInputPackageShouldDependOnMonitoringPackage() {
        val rule = noClasses()
            .that()
            .resideInAPackage("..input")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..monitoring")

        rule.check(noTestClasses)
    }

    @Test
    fun noClassesInInputPackageShouldDependOnRepositories() {
        val rule = noClasses()
            .that()
            .resideInAPackage("..input")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")

        rule.check(noTestClasses)
    }

    @Test
    fun noClassesInOutputPackageShouldDependOnRepositories() {
        val rule = noClasses()
            .that()
            .resideInAPackage("..output")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")

        rule.check(noTestClasses)
    }

    @Test
    fun noClassesInWebPackageShouldDependOnRepositories() {
        val rule = noClasses()
            .that()
            .resideInAPackage("..web")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")

        rule.check(noTestClasses)
    }

    @Test
    fun repositoryClassNamesShouldEndWithRepository() {
        val rule = classes()
            .that()
            .areInterfaces().and().areAssignableTo(Repository::class.java)
            .should().haveSimpleNameEndingWith("Repository")

        rule.check(noTestClasses)
    }

}