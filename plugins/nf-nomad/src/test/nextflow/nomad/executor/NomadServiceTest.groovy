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
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.util.Duration
import nextflow.util.MemoryUnit
import spock.lang.Specification

import java.nio.file.Paths

/**
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
class NomadServiceTest extends Specification {

    def RANDOM_ID = Math.abs(new Random().nextInt() % 999) + 1
    def NF_TASKJOB_NAME =  "nf-service-test-$RANDOM_ID"

    def 'should make job id'() {
        given:
        def task = Mock(TaskRun) {
            getProcessor() >> Mock(TaskProcessor) {
                getName() >> NAME
            }
        }
        and:
        def exec = Mock(NomadExecutor) {
            getConfig() >> new NomadConfig([:])
        }
        and:
        def svc = new NomadService(exec)

        expect:
        svc.makeJobId(task) =~ EXPECTED

        where:
        NAME        | EXPECTED
        'foo'       | /nf-foo-\w+/
        'foo  bar'  | /nf-foo_bar-\w+/
    }

    def 'should create and submit a job'() {
        given:

        def CONFIG_MAP = [nomad: [client: [namespace: "default"]]]

        and:
        def exec = Mock(NomadExecutor) {getConfig() >> new NomadConfig(CONFIG_MAP) }
        def svc = Spy(new NomadService(exec))

        def TASK = Mock(TaskRun) {
            getHash() >> HashCode.fromInt(1)
            getContainer() >> 'quay.io/nextflow/rnaseq-nf:v1.1'
            getWorkDir() >> Paths.get('/opt/nomad/_scratch/workdir')
            getScript() >> getClass().getResource("/ServiceTest.command.sh").text
            getConfig() >> Mock(TaskConfig) {
                getShell() >> ["bash"]
                getCpus() >> 4
                getMemory() >> new MemoryUnit("400.MB")
                getTime() >> new Duration("55s")
            }
            getProcessor() >> Mock(TaskProcessor) {
                getName() >> "svctest"
            }
        }


        and:
        println(TASK.hashCode())
        def jobId = svc.getOrRunJob(TASK)

        expect:
        println(svc.allJobIds)
        println(jobId)
    }
}
