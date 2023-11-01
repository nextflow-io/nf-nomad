/*
 * Copyright 2023, Stellenbosch University, South Africa
 * Copyright 2022, Center for Medical Genetics, Ghent
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

package nextflow.nomad.model

import com.google.common.hash.HashCode
import nextflow.nomad.NomadHelper
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.executor.NomadExecutor
import nextflow.nomad.executor.NomadService
import nextflow.processor.TaskConfig
import nextflow.processor.TaskRun
import spock.lang.Specification

import java.util.concurrent.ThreadLocalRandom

/**
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
class NomadJobBuilderTest extends Specification {
    def 'should create a Nomad job definition from Nextflow task' () {
        given:
        def randomNuber = ThreadLocalRandom.current().nextInt(100, 999 + 1)

        def exec = Mock(NomadExecutor) {
            getConfig() >> new NomadConfig([:])
        }
        def svc = new NomadService(exec)
        def container = "quay.io/nextflow/rnaseq-nf:v1.1"
        def TASK = Mock(TaskRun) {
            getHash() >> HashCode.fromInt(randomNuber)
            getName() >> "test-${randomNuber}"
            getContainer() >> container
            getConfig() >> Mock(TaskConfig)
        }

        when:
        def result = NomadHelper.createJob(TASK)

        then:
        TASK.container == container
        println(result)

    }

}
