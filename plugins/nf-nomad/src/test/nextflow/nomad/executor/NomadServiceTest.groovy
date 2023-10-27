/*
 * Copyright 2013-2023, Seqera Labs
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
import spock.lang.Specification

import java.util.concurrent.ThreadLocalRandom

/**
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
class NomadServiceTest extends Specification {

//    def 'should get status' () {
//        given:
//
//        def exec = Mock(NomadExecutor) {
//            getConfig() >> new NomadConfig([:])
//        }
//        def svc = new NomadService(exec)
//
//        when:
//        def result = svc.serverStatus()
//
//        then:
//        //FIXME refactor to test only the server base url
//        result.json[0] == exec.config.client().server
//    }


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
        def result = svc.createJob(TASK)

        then:
        TASK.container == container
        println(result)

    }


    def 'should submit a job' () {
        given:
        def randomNuber = ThreadLocalRandom.current().nextInt(100, 999 + 1)

        def exec = Mock(NomadExecutor) {
            getConfig() >> new NomadConfig([:])
        }
        def svc = new NomadService(exec)
        def TASK = Mock(TaskRun) {
            getHash() >> HashCode.fromInt(randomNuber)
            getName() >> "test-${randomNuber}"
            getContainer() >> "quay.io/nextflow/rnaseq-nf:v1.1"
            getConfig() >> Mock(TaskConfig)
        }

        when:
        def result = svc.jobSubmit(TASK)

        then:
        result.json.EvalID != null

    }




    def 'should list jobs' () {
        given:

        def exec = Mock(NomadExecutor) {
            getConfig() >> new NomadConfig([:])
        }
        def svc = new NomadService(exec)

        when:
        def result = svc.jobList()

        then:
        result.json[0].Namespace == exec.config.client().namespace

    }


    def 'should fetch job summary' () {
        given:

        def exec = Mock(NomadExecutor) {
            getConfig() >> new NomadConfig([:])
        }
        def svc = new NomadService(exec)

        when:
        def JOB_NAME = "sleep"
        def result = svc.jobSummary(JOB_NAME)

        then:
        println(result.json.Summary."$JOB_NAME-group")

    }

    def 'should delete a job' () {
        given:
        def randomNuber = ThreadLocalRandom.current().nextInt(100, 999 + 1)

        def exec = Mock(NomadExecutor) {
            getConfig() >> new NomadConfig([:])
        }
        def svc = new NomadService(exec)

        when:
        def result = svc.jobPurge("nf-test-401-91010000-id")


        then:
        result.json.EvalID != null

    }


}