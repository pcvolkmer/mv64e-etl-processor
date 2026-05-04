/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023       Comprehensive Cancer Center Mainfranken
 * Copyright (c) 2026  Paul-Christian Volkmer, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
