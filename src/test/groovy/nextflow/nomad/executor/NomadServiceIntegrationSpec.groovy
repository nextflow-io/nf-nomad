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

import groovy.json.JsonSlurper
import nextflow.executor.Executor
import nextflow.nomad.config.NomadConfig
import nextflow.processor.TaskBean
import nextflow.processor.TaskConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Timeout

import java.nio.file.Path

/**
 * Integration tests for NomadService against live Nomad clusters.
 *
 * Activated when NF_NOMAD_TEST_ENV is 'oci' or 'local'.
 * Requires NOMAD_ADDR (and optionally NOMAD_TOKEN / NOMAD_DC) in the environment.
 *
 * Run with:
 *   ./gradlew test -PtestEnv=local
 *   ./gradlew test -PtestEnv=oci
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Timeout(120)
@Stepwise
@Requires({ System.getenv('NF_NOMAD_TEST_ENV') in ['oci', 'local'] })
class NomadServiceIntegrationSpec extends Specification {

    @Shared NomadConfig config
    @Shared NomadService service
    @Shared String submittedJobId

    def setupSpec() {
        def addr  = System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'
        def token = System.getenv('NOMAD_TOKEN') ?: null
        def dc    = System.getenv('NOMAD_DC') ?: null

        def clientOpts = [address: addr]
        if (token) clientOpts.token = token

        def jobsOpts = [:]
        if (dc) jobsOpts.datacenters = dc

        config  = new NomadConfig(client: clientOpts, jobs: jobsOpts)
        service = new NomadService(config)
    }

    def cleanupSpec() {
        if (submittedJobId) {
            try { service.jobPurge(submittedJobId) } catch (ignored) {}
        }
        service?.close()
    }

    // ------------------------------------------------------------------
    // Connectivity
    // ------------------------------------------------------------------

    void "should connect to the Nomad cluster"() {
        expect: "the API client base path points at the configured address"
        service.apiClient.basePath.startsWith(
                (System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646')
        )
    }

    // ------------------------------------------------------------------
    // Submit a minimal batch job
    // ------------------------------------------------------------------

    void "should submit a simple batch job"() {
        given:
        submittedJobId = "integration-test-${System.currentTimeMillis()}"

        def mockTask = Mock(TaskRun) {
            getName()      >> "integration-hello"
            getContainer() >> "ubuntu:22.04"
            getConfig()    >> Mock(TaskConfig)
            getWorkDirStr() >> "/tmp"
            getProcessor() >> Mock(TaskProcessor) {
                getExecutor() >> Mock(Executor) {
                    isFusionEnabled() >> false
                }
            }
            getWorkDir()   >> Path.of("/tmp")
            toTaskBean()   >> Mock(TaskBean) {
                getWorkDir()    >> Path.of("/tmp")
                getScript()     >> 'echo hello'
                getShell()      >> ["bash"]
                getInputFiles() >> [:]
            }
        }

        when:
        def evalId = service.submitTask(
                submittedJobId,
                mockTask,
                ["bash", "-c", "echo hello"],
                [NF_INTEGRATION_TEST: "true"]
        )

        then:
        evalId != null
        evalId.size() > 0
    }

    // ------------------------------------------------------------------
    // Poll task state
    // ------------------------------------------------------------------

    void "should retrieve task state for submitted job"() {
        expect: "submittedJobId was set by the previous test"
        submittedJobId != null

        when:
        def state = service.getTaskState(submittedJobId)

        then: "state is returned (may be pending, running, or dead)"
        state != null
        state.state != null
    }

    // ------------------------------------------------------------------
    // Purge
    // ------------------------------------------------------------------

    void "should purge the submitted job"() {
        expect:
        submittedJobId != null

        when:
        service.jobPurge(submittedJobId)

        then:
        noExceptionThrown()

        cleanup:
        submittedJobId = null  // already purged
    }

    // ------------------------------------------------------------------
    // OCI-specific: token-based auth smoke test
    // ------------------------------------------------------------------

    @Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'oci' })
    void "should have a token configured for OCI cluster"() {
        expect:
        config.clientOpts.token != null
        config.clientOpts.token.size() > 0
    }

    // ------------------------------------------------------------------
    // Local-specific: default address smoke test
    // ------------------------------------------------------------------

    @Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'local' })
    void "should target localhost for local cluster"() {
        expect:
        service.apiClient.basePath.contains("localhost")
    }
}
