/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2024  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

package dev.dnpm.etl.processor.security

import java.time.Instant
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.security.core.session.SessionInformation
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser

@ExtendWith(MockitoExtension::class)
class UserRoleServiceTest {

  private lateinit var userRoleRepository: UserRoleRepository
  private lateinit var sessionRegistry: SessionRegistry

  private lateinit var userRoleService: UserRoleService

  @BeforeEach
  fun setup(@Mock userRoleRepository: UserRoleRepository, @Mock sessionRegistry: SessionRegistry) {
    this.userRoleRepository = userRoleRepository
    this.sessionRegistry = sessionRegistry

    this.userRoleService = UserRoleService(userRoleRepository, sessionRegistry)
  }

  @Test
  fun shouldDelegateFindAllToRepository() {
    userRoleService.findAll()

    verify(userRoleRepository, times(1)).findAll()
  }

  @Nested
  inner class WithExistingUserRole {

    @BeforeEach
    fun setup() {
      doAnswer { invocation ->
            Optional.of(UserRole(invocation.getArgument(0), "patrick.tester", Role.USER))
          }
          .whenever(userRoleRepository)
          .findById(any<Long>())

      doAnswer { _ -> listOf(dummyPrincipal()) }.whenever(sessionRegistry).allPrincipals
    }

    @Test
    fun shouldUpdateUserRole() {
      userRoleService.updateUserRole(1, Role.ADMIN)

      val userRoleCaptor = argumentCaptor<UserRole>()
      verify(userRoleRepository, times(1)).save(userRoleCaptor.capture())

      assertThat(userRoleCaptor.firstValue.id).isEqualTo(1L)
      assertThat(userRoleCaptor.firstValue.role).isEqualTo(Role.ADMIN)
    }

    @Test
    fun shouldExpireSessionOnUpdate() {
      val dummySessions = dummySessions()
      whenever(sessionRegistry.getAllSessions(any(), any<Boolean>())).thenReturn(dummySessions)

      assertThat(dummySessions.filter { it.isExpired }).hasSize(0)

      userRoleService.updateUserRole(1, Role.ADMIN)

      verify(sessionRegistry, times(1)).getAllSessions(any<OidcUser>(), any<Boolean>())

      assertThat(dummySessions.filter { it.isExpired }).hasSize(2)
    }

    @Test
    fun shouldDeleteUserRole() {
      userRoleService.deleteUserRole(1)

      val userRoleCaptor = argumentCaptor<UserRole>()
      verify(userRoleRepository, times(1)).delete(userRoleCaptor.capture())

      assertThat(userRoleCaptor.firstValue.id).isEqualTo(1L)
      assertThat(userRoleCaptor.firstValue.role).isEqualTo(Role.USER)
    }

    @Test
    fun shouldExpireSessionOnDelete() {
      val dummySessions = dummySessions()
      whenever(sessionRegistry.getAllSessions(any(), any<Boolean>())).thenReturn(dummySessions)

      assertThat(dummySessions.filter { it.isExpired }).hasSize(0)

      userRoleService.deleteUserRole(1)

      verify(sessionRegistry, times(1)).getAllSessions(any<OidcUser>(), any<Boolean>())

      assertThat(dummySessions.filter { it.isExpired }).hasSize(2)
    }
  }

  @Nested
  inner class WithoutExistingUserRole {

    @BeforeEach
    fun setup() {
      doAnswer { _ -> Optional.empty<UserRole>() }
          .whenever(userRoleRepository)
          .findById(any<Long>())
    }

    @Test
    fun shouldNotUpdateUserRole() {
      userRoleService.updateUserRole(1, Role.ADMIN)

      verify(userRoleRepository, never()).save(any<UserRole>())
    }

    @Test
    fun shouldNotExpireSessionOnUpdate() {
      userRoleService.updateUserRole(1, Role.ADMIN)

      verify(sessionRegistry, never()).getAllSessions(any<OidcUser>(), any<Boolean>())
    }

    @Test
    fun shouldNotDeleteUserRole() {
      userRoleService.deleteUserRole(1)

      verify(userRoleRepository, never()).delete(any<UserRole>())
    }

    @Test
    fun shouldNotExpireSessionOnDelete() {
      userRoleService.deleteUserRole(1)

      verify(sessionRegistry, never()).getAllSessions(any<OidcUser>(), any<Boolean>())
    }
  }

  companion object {
    private fun dummyPrincipal() =
        DefaultOidcUser(
            listOf(),
            OidcIdToken(
                "anytokenvalue",
                Instant.now(),
                Instant.now().plusSeconds(10),
                mapOf("sub" to "testsub", "preferred_username" to "patrick.tester"),
            ),
        )

    private fun dummySessions() =
        listOf(
            SessionInformation(
                dummyPrincipal(),
                "SESSIONID1",
                Date.from(Instant.now()),
            ),
            SessionInformation(
                dummyPrincipal(),
                "SESSIONID2",
                Date.from(Instant.now()),
            ),
        )
  }
}
