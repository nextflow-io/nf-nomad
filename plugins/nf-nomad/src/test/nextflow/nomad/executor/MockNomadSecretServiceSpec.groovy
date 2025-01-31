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


import nextflow.nomad.config.NomadConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Requires
import spock.lang.Specification

/**
 * Unit test for Nomad Service for Secrets requests
 *
 * Validate requests using a Mock WebServer
 *
 * @author : Jorge Aguilera <jagedn@gmail.com>
 */

@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'mock' })
class MockNomadSecretServiceSpec extends Specification{

    MockWebServer mockWebServer

    def setup() {
        mockWebServer = new MockWebServer()
        mockWebServer.start()
    }

    def cleanup() {
        mockWebServer.shutdown()
    }

    void "should request a variable"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
                jobs: [
                        secrets:[
                                enable: true,
                                path: 'test'
                        ]
                ]
        )
        def service = new NomadService(config)

        when:
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json"));

        service.getVariableValue("MySecret")
        def recordedRequest = mockWebServer.takeRequest();

        then:
        recordedRequest.method == "GET"
        recordedRequest.path == "/v1/var/test%2FMySecret"

        when:
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json"));

        service.getVariableValue("another", "MySecret")
        def recordedRequest2 = mockWebServer.takeRequest();

        then:
        recordedRequest2.method == "GET"
        recordedRequest2.path == "/v1/var/another%2FMySecret"
    }

    @Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'mock' })
    void "should set a variable"(){
        given:
        def config = new NomadConfig(
                client:[
                        address : "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
                jobs: [
                        secrets:[
                                enable: true,
                                path: 'test'
                        ]
                ]
        )
        def service = new NomadService(config)

        when:
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json"));

        service.setVariableValue("MySecret", "MyValue")
        def recordedRequest = mockWebServer.takeRequest();

        then:
        recordedRequest.method == "POST"
        recordedRequest.path == "/v1/var/test%2FMySecret"
    }

}
