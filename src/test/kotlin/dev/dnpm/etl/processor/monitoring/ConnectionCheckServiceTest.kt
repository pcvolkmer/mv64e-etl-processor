package dev.dnpm.etl.processor.monitoring

import dev.dnpm.etl.processor.config.GIcsConfigProperties
import dev.dnpm.etl.processor.config.GPasConfigProperties
import dev.dnpm.etl.processor.config.RestTargetProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier

@ExtendWith(MockitoExtension::class)
class ConnectionCheckServiceTest {

    @Nested
    inner class RestConnectionCheckServiceTest {

        lateinit var mockRestServiceServer: MockRestServiceServer
        lateinit var service: RestConnectionCheckService
        lateinit var sink: Sinks.Many<ConnectionCheckResult>

        @BeforeEach
        fun setUp() {
            val restTemplate = RestTemplate()
            val restTargetProperties = RestTargetProperties(
                "http://localhost/api",
                "user",
                "password",
            )
            this.sink = Sinks.many().multicast().onBackpressureBuffer()
            this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate)

            this.service = RestConnectionCheckService(restTemplate, restTargetProperties, sink)
        }

        @Test
        fun shouldSendRequestToCorrectUri() {
            this.mockRestServiceServer
                .expect(method(HttpMethod.GET))
                .andExpect(requestTo("http://localhost/api/mtb/kaplan-meier/config"))
                .andRespond(
                    withSuccess("OK", MediaType.APPLICATION_JSON),
                )

            this.service.check()
            this.mockRestServiceServer.verify()
        }

        @Test
        fun shouldEmitAvailable() {
            this.mockRestServiceServer
                .expect(method(HttpMethod.GET))
                .andRespond(
                    withSuccess("OK", MediaType.APPLICATION_JSON),
                )

            val verifier = StepVerifier.create(sink.asFlux())
                .assertNext {
                    assertThat(it.available).isTrue()
                }
                .expectComplete()
                .verifyLater()

            this.service.check()

            this.sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST)

            verifier.verify()
        }

        @Test
        fun shouldEmitUnavailable() {
            this.mockRestServiceServer
                .expect(method(HttpMethod.GET))
                .andRespond(
                    withServerError()
                )

            val verifier = StepVerifier.create(sink.asFlux())
                .assertNext {
                    assertThat(it.available).isFalse()
                }
                .expectComplete()
                .verifyLater()

            this.service.check()

            this.sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST)

            verifier.verify()
        }
    }

    @Nested
    inner class GPasConnectionCheckServiceTest {

        lateinit var mockRestServiceServer: MockRestServiceServer
        lateinit var service: GPasConnectionCheckService
        lateinit var sink: Sinks.Many<ConnectionCheckResult>

        @BeforeEach
        fun setUp() {
            val restTemplate = RestTemplate()
            val gpasTargetProperties = GPasConfigProperties(
                "http://localhost/gpas",
                null,
                "patientDomain",
                "genomDeTanDomain",
                "username",
                "password",
            )
            this.sink = Sinks.many().multicast().onBackpressureBuffer()
            this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate)

            this.service = GPasConnectionCheckService(restTemplate, gpasTargetProperties, sink)
        }

        @Test
        fun shouldSendRequestToCorrectUri() {
            this.mockRestServiceServer
                .expect(method(HttpMethod.GET))
                .andExpect(requestTo("http://localhost/gpas/metadata"))
                .andRespond(
                    withSuccess("OK", MediaType.APPLICATION_JSON),
                )

            this.service.check()

            this.mockRestServiceServer.verify()
        }

        @Test
        fun shouldEmitAvailable() {
            this.mockRestServiceServer
                .expect(method(HttpMethod.GET))
                .andRespond(
                    withSuccess("OK", MediaType.APPLICATION_JSON),
                )

            val verifier = StepVerifier.create(sink.asFlux())
                .assertNext {
                    assertThat(it.available).isTrue()
                }
                .expectComplete()
                .verifyLater()

            this.service.check()

            this.sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST)

            verifier.verify()
        }

        @Test
        fun shouldEmitUnavailable() {
            this.mockRestServiceServer
                .expect(method(HttpMethod.GET))
                .andRespond(
                    withServerError()
                )

            val verifier = StepVerifier.create(sink.asFlux())
                .assertNext {
                    assertThat(it.available).isFalse()
                }
                .expectComplete()
                .verifyLater()

            this.service.check()

            this.sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST)

            verifier.verify()
        }
    }

    @Nested
    inner class GIcsConnectionCheckServiceTest {

        lateinit var mockRestServiceServer: MockRestServiceServer
        lateinit var service: GIcsConnectionCheckService
        lateinit var sink: Sinks.Many<ConnectionCheckResult>

        @BeforeEach
        fun setUp() {
            val restTemplate = RestTemplate()

            val gicsTargetProperties = GIcsConfigProperties(
                "http://localhost/gics",
                "username",
                "password",
            )
            this.sink = Sinks.many().multicast().onBackpressureBuffer()
            this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate)

            this.service = GIcsConnectionCheckService(restTemplate, gicsTargetProperties, sink)
        }

        @Test
        fun shouldSendRequestToCorrectUri() {
            this.mockRestServiceServer
                .expect(method(HttpMethod.GET))
                .andExpect(requestTo("http://localhost/gics/metadata"))
                .andRespond(
                    withSuccess("OK", MediaType.APPLICATION_JSON),
                )

            this.service.check()

            this.mockRestServiceServer.verify()

        }

        @Test
        fun shouldEmitAvailable() {
            this.mockRestServiceServer
                .expect(method(HttpMethod.GET))
                .andRespond(
                    withSuccess("OK", MediaType.APPLICATION_JSON),
                )

            val verifier = StepVerifier.create(sink.asFlux())
                .assertNext {
                    assertThat(it.available).isTrue()
                }
                .expectComplete()
                .verifyLater()

            this.service.check()

            this.sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST)

            verifier.verify()
        }

        @Test
        fun shouldEmitUnavailable() {
            this.mockRestServiceServer
                .expect(method(HttpMethod.GET))
                .andRespond(
                    withServerError()
                )

            val verifier = StepVerifier.create(sink.asFlux())
                .assertNext {
                    assertThat(it.available).isFalse()
                }
                .expectComplete()
                .verifyLater()

            this.service.check()

            this.sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST)

            verifier.verify()
        }
    }

}