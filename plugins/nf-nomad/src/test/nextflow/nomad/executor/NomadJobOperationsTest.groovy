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

package nextflow.nomad.executor

import com.google.common.hash.HashCode
import nextflow.nomad.config.NomadConfig
import nextflow.processor.TaskConfig
import nextflow.processor.TaskRun
import nextflow.util.MemoryUnit
import spock.lang.Specification

/**
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
class NomadJobOperationsTest extends Specification {

    def RANDOM_ID = Math.abs(new Random().nextInt() % 999) + 1
    def NF_TASKJOB_NAME =  "nf-service-test-$RANDOM_ID"

    def TEST_CONTAINER_NAME = "quay.io/nextflow/rnaseq-nf:v1.1"

    def 'should create a job definition'() {
        given:
        def CONFIG_MAP = [nomad: [client: [namespace: "default"]]]
        def config = new NomadConfig(CONFIG_MAP)

        and:
        def TASK = Mock(TaskRun) {
            getHash() >> HashCode.fromInt(1)
            getContainer() >> TEST_CONTAINER_NAME
            getScript() >> getClass().getResource("/ServiceTest.command.sh").text
            getConfig() >> Mock(TaskConfig) {
                getShell() >> ["bash"]
                getCpus() >> 4
                getMemory() >> new MemoryUnit(4000)
            }
        }

        and:
        def job = NomadJobOperations.createJobDef(config, TASK, NF_TASKJOB_NAME)

        expect:
        job.name == NF_TASKJOB_NAME
        job.taskGroups.tasks[0].config[0]['image'] == TEST_CONTAINER_NAME

    }

}
