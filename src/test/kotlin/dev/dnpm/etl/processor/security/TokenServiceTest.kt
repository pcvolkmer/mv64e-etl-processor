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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import java.util.*
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
class TokenServiceTest {

    private lateinit var userDetailsManager: InMemoryUserDetailsManager
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var tokenRepository: TokenRepository

    private lateinit var tokenService: TokenService

    @BeforeEach
    fun setup(
        @Mock userDetailsManager: InMemoryUserDetailsManager,
        @Mock passwordEncoder: PasswordEncoder,
        @Mock tokenRepository: TokenRepository
    ) {
        this.userDetailsManager = userDetailsManager
        this.passwordEncoder = passwordEncoder
        this.tokenRepository = tokenRepository

        this.tokenService = TokenService(userDetailsManager, passwordEncoder, tokenRepository)
    }

    @Test
    fun shouldEncodePasswordForNewToken() {
        doAnswer { "{test}verysecret" }.whenever(passwordEncoder).encode(anyString())

        val actual = this.tokenService.addToken("Test Token")

        assertThat(actual).satisfies(
            Consumer { assertThat(it.isSuccess).isTrue() },
            Consumer { assertThat(it.getOrNull()).matches("testtoken:[A-Za-z0-9]{48}$") }
        )
    }

    @Test
    fun shouldContainAlphanumTokenUserPart() {
        doAnswer { "{test}verysecret" }.whenever(passwordEncoder).encode(anyString())

        val actual = this.tokenService.addToken("Test Token")

        assertThat(actual).satisfies(
            Consumer { assertThat(it.isSuccess).isTrue() },
            Consumer { assertThat(it.getOrDefault("")).startsWith("testtoken:") }
        )
    }

    @Test
    fun shouldNotAllowSameTokenUserPartTwice() {
        doReturn(true).whenever(userDetailsManager).userExists(anyString())

        val actual = this.tokenService.addToken("Test Token")

        assertThat(actual).satisfies(Consumer { assertThat(it.isFailure).isTrue() })
        verify(tokenRepository, never()).save(any())
    }

    @Test
    fun shouldSaveNewToken() {
        doAnswer { "{test}verysecret" }.whenever(passwordEncoder).encode(anyString())

        val actual = this.tokenService.addToken("Test Token")

        val captor = ArgumentCaptor.forClass(Token::class.java)
        verify(tokenRepository, times(1)).save(captor.capture())

        assertThat(actual).satisfies(Consumer { assertThat(it.isSuccess).isTrue() })
        assertThat(captor.value).satisfies(
            Consumer { assertThat(it.name).isEqualTo("Test Token") },
            Consumer { assertThat(it.username).isEqualTo("testtoken") },
            Consumer { assertThat(it.password).isEqualTo("{test}verysecret") }
        )
    }

    @Test
    fun shouldDeleteExistingToken() {
        doAnswer {
            val id = it.arguments[0] as Long
            Optional.of(Token(id, "Test Token", "testtoken", "{test}hsdajfgadskjhfgsdkfjg"))
        }.whenever(tokenRepository).findById(anyLong())

        this.tokenService.deleteToken(42)

        val stringCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(userDetailsManager, times(1)).deleteUser(stringCaptor.capture())
        assertThat(stringCaptor.value).isEqualTo("testtoken")

        val tokenCaptor = ArgumentCaptor.forClass(Token::class.java)
        verify(tokenRepository, times(1)).delete(tokenCaptor.capture())
        assertThat(tokenCaptor.value.id).isEqualTo(42)
    }

    @Test
    fun shouldReturnAllTokensFromRepository() {
        val expected = listOf(
            Token(1, "Test Token 1", "testtoken1", "{test}hsdajfgadskjhfgsdkfjg"),
            Token(2, "Test Token 2", "testtoken2", "{test}asdasdasdasdasdasdasd")
        )

        doReturn(expected).whenever(tokenRepository).findAll()

        assertThat(tokenService.findAll()).isEqualTo(expected)
    }

    @Test
    fun shouldAddAllTokensFromRepositoryToUserDataManager() {
        val expected = listOf(
            Token(1, "Test Token 1", "testtoken1", "{test}hsdajfgadskjhfgsdkfjg"),
            Token(2, "Test Token 2", "testtoken2", "{test}asdasdasdasdasdasdasd")
        )

        doReturn(expected).whenever(tokenRepository).findAll()

        tokenService.setup()

        verify(userDetailsManager, times(expected.size)).createUser(any())
    }

}