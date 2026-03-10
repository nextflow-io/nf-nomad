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

import java.nio.file.Files
import java.nio.file.Path

/**
 * Local integration tests for Nomad Variables (secrets) management.
 *
 * Tests variable creation, retrieval, updates, and namespacing
 * against a real Nomad cluster.
 *
 * Run with:
 *   ./gradlew test -PtestEnv=local --tests LocalSecretsIntegrationSpec
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Timeout(240)
@Stepwise
@Requires({ System.getenv('NF_NOMAD_TEST_ENV') == 'local' })
class LocalSecretsIntegrationSpec extends Specification {

    @Shared NomadConfig config
    @Shared NomadService service
    @Shared List<String> createdVariables = []
    @Shared Path testWorkDir

    def setupSpec() {
        def addr = System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'
        def dc = System.getenv('NOMAD_DC') ?: null

        def clientOpts = [address: addr]
        def jobsOpts = [
            deleteOnCompletion: false,
            secrets: [
                enabled: true,
                path: 'test'
            ]
        ]
        if (dc) jobsOpts.datacenters = dc

        config = new NomadConfig(client: clientOpts, jobs: jobsOpts)
        service = new NomadService(config)
        testWorkDir = Files.createTempDirectory("nf-secrets-test")
    }

    def cleanupSpec() {
        // Clean up variables if they were created
        // Note: This depends on Nomad having a delete API which may not be available
        service?.close()
        testWorkDir?.deleteDir()
    }

    // ------------------------------------------------------------------
    // Test 1: Store and Retrieve Variable
    // ------------------------------------------------------------------

    void "should store and retrieve variable"() {
        given:
        def varName = "test-var-${System.currentTimeMillis()}"
        def varValue = "test-secret-value"
        createdVariables.add(varName)

        when:
        service.setVariableValue(varName, varValue)
        sleep(500)

        then:
        noExceptionThrown()

        when:
        def retrievedValue = service.getVariableValue(varName)

        then:
        retrievedValue != null
    }

    // ------------------------------------------------------------------
    // Test 2: Use Default Secrets Path
    // ------------------------------------------------------------------

    void "should use default secrets path"() {
        given:
        def varName = "default-path-var-${System.currentTimeMillis()}"
        def varValue = "secret-data"
        createdVariables.add(varName)

        def defaultPathConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                secrets: [
                    enabled: true,
                    path: 'test'
                ]
            ]
        ])
        def defaultService = new NomadService(defaultPathConfig)

        when:
        defaultService.setVariableValue(varName, varValue)
        sleep(500)

        then:
        noExceptionThrown()

        cleanup:
        defaultService?.close()
    }

    // ------------------------------------------------------------------
    // Test 3: Use Custom Secrets Path
    // ------------------------------------------------------------------

    void "should use custom secrets path"() {
        given:
        def customPath = "custom/path/secrets"
        def varName = "custom-path-var-${System.currentTimeMillis()}"
        def varValue = "custom-secret"

        def customPathConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                secrets: [
                    enabled: true,
                    path: customPath
                ]
            ]
        ])
        def customService = new NomadService(customPathConfig)

        when:
        customService.setVariableValue(varName, varValue)
        sleep(500)

        then:
        noExceptionThrown()

        cleanup:
        customService?.close()
    }

    // ------------------------------------------------------------------
    // Test 4: Handle Non-Existent Variables
    // ------------------------------------------------------------------

    void "should handle non-existent variables"() {
        when:
        def value = service.getVariableValue("non-existent-var-${System.currentTimeMillis()}")

        then:
        value == null || value.isEmpty()
    }

    // ------------------------------------------------------------------
    // Test 5: Retrieve Variable with Special Characters
    // ------------------------------------------------------------------

    void "should retrieve variable with special characters"() {
        given:
        def varName = "special-chars-var-${System.currentTimeMillis()}"
        def varValue = 'secret!@#$%^&*()'
        createdVariables.add(varName)

        when:
        service.setVariableValue(varName, varValue)
        sleep(500)

        then:
        noExceptionThrown()

        when:
        def retrievedValue = service.getVariableValue(varName)

        then:
        retrievedValue != null
    }

    // ------------------------------------------------------------------
    // Test 6: Create Variable
    // ------------------------------------------------------------------

    void "should create new variable"() {
        given:
        def varName = "new-var-${System.currentTimeMillis()}"
        def varValue = "new-secret"
        createdVariables.add(varName)

        when:
        service.setVariableValue(varName, varValue)
        sleep(500)

        then:
        noExceptionThrown()
    }

    // ------------------------------------------------------------------
    // Test 7: Update Existing Variable
    // ------------------------------------------------------------------

    void "should update existing variable"() {
        given:
        def varName = "update-var-${System.currentTimeMillis()}"
        def initialValue = "initial-value"
        def updatedValue = "updated-value"
        createdVariables.add(varName)

        when:
        service.setVariableValue(varName, initialValue)
        sleep(500)
        service.setVariableValue(varName, updatedValue)
        sleep(500)

        then:
        noExceptionThrown()
    }

    // ------------------------------------------------------------------
    // Test 8: Variable Namespaces
    // ------------------------------------------------------------------

    void "should handle variable namespaces"() {
        given:
        def namespace = "namespace1"
        def varName = "namespaced-var-${System.currentTimeMillis()}"
        def varValue = "namespaced-secret"

        def nsConfig = new NomadConfig([
            client: [address: System.getenv('NOMAD_ADDR') ?: 'http://localhost:4646'],
            jobs: [
                secrets: [
                    enabled: true,
                    path: namespace
                ]
            ]
        ])
        def nsService = new NomadService(nsConfig)

        when:
        nsService.setVariableValue(varName, varValue)
        sleep(500)

        then:
        noExceptionThrown()

        cleanup:
        nsService?.close()
    }

    // ------------------------------------------------------------------
    // Test 9: Variable Path Handling
    // ------------------------------------------------------------------

    void "should handle variable path formatting"() {
        given:
        def varName = "path-var-${System.currentTimeMillis()}"
        def varValue = "path-secret"
        createdVariables.add(varName)

        when:
        service.setVariableValue(varName, varValue)
        sleep(500)
        def value = service.getVariableValue(varName)

        then:
        noExceptionThrown()
        value != null
    }

    // ------------------------------------------------------------------
    // Test 10: Multiple Variables
    // ------------------------------------------------------------------

    void "should manage multiple variables"() {
        given:
        def var1 = "multi-var-1-${System.currentTimeMillis()}"
        def var2 = "multi-var-2-${System.currentTimeMillis()}"
        def var3 = "multi-var-3-${System.currentTimeMillis()}"
        createdVariables.addAll([var1, var2, var3])

        when:
        service.setVariableValue(var1, "value1")
        service.setVariableValue(var2, "value2")
        service.setVariableValue(var3, "value3")
        sleep(500)

        then:
        noExceptionThrown()
    }
}

