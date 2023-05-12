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
import nextflow.Session
import nextflow.nomad.config.NomadConfig
import nextflow.nomad.executor.NomadService
import nextflow.processor.TaskConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import spock.lang.Specification
/**
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
class NomadServiceTest extends Specification {

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
        'foo'       | /job-\w+-foo/
        'foo  bar'  | /job-\w+-foo_bar/
    }


    def 'should submit a job'() {
        given:

        def RANDOM_ID = Math.abs(new Random().nextInt() % 999) + 1
        def NF_TASKJOB_NAME =  "nf-$RANDOM_ID"

        def exec = Mock(NomadExecutor) {
            getConfig() >> new NomadConfig([nomad:
                                                    [client:
                                                             [serverBasePath:NOMAD_ADDR,
                                                              apiToken:NOMAD_TOKEN ,
                                                              dataCenter: NOMAD_DATACENTER]]])
        }
        def svc = new NomadService(exec)

        and:
        def TASK = Mock(TaskRun) {
            getHash() >> HashCode.fromInt(1)
            getContainer() >> '"quay.io/nextflow/rnaseq-nf:v1.1"'
            getConfig() >> Mock(TaskConfig)
        }

//        and:
//
//        expect:
//        svc.createTaskJob(id, TASK)

    }





}
