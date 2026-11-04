/*
 * Copyright 2023-, Stellenbosch University, South Africa
 * Copyright 2024, Evaluacion y Desarrollo de Negocios, Spain
 * Copyright 2026-, Incremental Steps Software Solutions OÜ
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

    /** isPlacementFailure() reads evaluations first, then falls back to allocations. */
    private void enqueueJson(Object body) {
        mockWebServer.enqueue(new MockResponse()
                .setBody(JsonOutput.toJson(body).toString())
                .addHeader("Content-Type", "application/json"))
    }

    private NomadService serviceWith(String timeout) {
        new NomadService(new NomadConfig(
                client: [
                        address: "http://${mockWebServer.hostName}:${mockWebServer.port}"
                ],
                jobs: [
                        failOnPlacementFailure: true,
                        placementFailureTimeout: timeout
                ]
        ))
    }

    void "placement failure should trigger when no allocations exist after timeout"() {
        given: 'no evaluation metrics, so the elapsed-time fallback decides'
        def service = serviceWith('5s')
        enqueueJson([])   // evaluations
        enqueueJson([])   // allocations

        when:
        boolean isFailure = service.isPlacementFailure("test-job", System.currentTimeMillis() - 10_000L)

        then:
        isFailure
    }

    void "placement failure should not trigger when no allocations exist before timeout"() {
        given:
        def service = serviceWith('2m')
        enqueueJson([])   // evaluations
        enqueueJson([])   // allocations

        when:
        boolean isFailure = service.isPlacementFailure("test-job", System.currentTimeMillis() - 10_000L)

        then:
        !isFailure
    }

    void "a task queued behind busy nodes is never a placement failure, however long it waits"() {
        given: 'the scheduler reports exhausted capacity -- eligible nodes exist and are simply full'
        def service = serviceWith('5s')
        enqueueJson([[
                ModifyIndex   : 42,
                FailedTGAllocs: [
                        'nf-task': [
                                DimensionExhausted: ['cpu': 1],
                                NodesEvaluated    : 1,
                                NodesAvailable    : ['dc1': 1]
                        ]
                ]
        ]])

        when: 'it has been waiting far longer than the timeout'
        boolean isFailure = service.isPlacementFailure("test-job", System.currentTimeMillis() - 600_000L)

        then: 'waiting for a busy node is the normal state on a saturated cluster, not a fault'
        !isFailure
    }

    void "a task no node can satisfy fails immediately, without waiting out the timeout"() {
        given: 'every node was rejected on a constraint -- waiting cannot change that'
        def service = serviceWith('30m')
        enqueueJson([[
                ModifyIndex   : 42,
                FailedTGAllocs: [
                        'nf-task': [
                                ConstraintFiltered: ['${node.class} = gpu': 3],
                                NodesEvaluated    : 3,
                                NodesFiltered     : 3
                        ]
                ]
        ]])

        when: 'barely any time has passed'
        boolean isFailure = service.isPlacementFailure("test-job", System.currentTimeMillis() - 1_000L)

        then: 'no point waiting out a constraint that will never be satisfied'
        isFailure
    }

    void "exhaustion wins over filtering when a cluster reports both"() {
        given: 'some nodes were filtered, but others matched and are merely full'
        def service = serviceWith('5s')
        enqueueJson([[
                ModifyIndex   : 42,
                FailedTGAllocs: [
                        'nf-task': [
                                ConstraintFiltered: ['${node.class} = gpu': 1],
                                DimensionExhausted: ['cpu': 1],
                                NodesEvaluated    : 2
                        ]
                ]
        ]])

        when:
        boolean isFailure = service.isPlacementFailure("test-job", System.currentTimeMillis() - 600_000L)

        then: 'a node did match, so the task is queued behind it'
        !isFailure
    }
}
