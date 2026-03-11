/*
 * Copyright 2023-, Stellenbosch University, South Africa
 * Copyright 2024, Evaluacion y Desarrollo de Negocios, Spain
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nextflow.nomad.executor

import groovy.json.JsonOutput
import nextflow.nomad.config.NomadConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Requires
import spock.lang.Specification

@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'mock' })
class MockNomadServicePlacementFailureSpec extends Specification {

    MockWebServer mockWebServer

    def setup() {
        mockWebServer = new MockWebServer()
        mockWebServer.start()
    }

    def cleanup() {
        mockWebServer.shutdown()
    }

    void "placement failure should trigger when no allocations exist after timeout"() {
        given:
        def config = new NomadConfig(
                client: [
                        address: "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
                jobs: [
                        failOnPlacementFailure: true,
                        placementFailureTimeout: '5s'
                ]
        )
        def service = new NomadService(config)

        mockWebServer.enqueue(new MockResponse()
                .setBody(JsonOutput.toJson([]).toString())
                .addHeader("Content-Type", "application/json"))

        when:
        boolean isFailure = service.isPlacementFailure("test-job", System.currentTimeMillis() - 10_000L)

        then:
        isFailure
    }

    void "placement failure should not trigger when no allocations exist before timeout"() {
        given:
        def config = new NomadConfig(
                client: [
                        address: "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
                jobs: [
                        failOnPlacementFailure: true,
                        placementFailureTimeout: '2m'
                ]
        )
        def service = new NomadService(config)

        mockWebServer.enqueue(new MockResponse()
                .setBody(JsonOutput.toJson([]).toString())
                .addHeader("Content-Type", "application/json"))

        when:
        boolean isFailure = service.isPlacementFailure("test-job", System.currentTimeMillis() - 10_000L)

        then:
        !isFailure
    }
}
