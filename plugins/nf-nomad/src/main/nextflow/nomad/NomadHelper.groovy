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

package nextflow.nomad

import nextflow.nomad.client.NomadResponseJson
import nextflow.nomad.model.NomadJobBuilder

import nextflow.processor.TaskRun
import nextflow.processor.TaskStatus

/**
 * Nomad API Helper methods
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */

class NomadHelper {

    static String sanitizeName(String name) {
        def str = "nf-${name}"
        str = str.replaceAll(/[^a-zA-Z0-9\.\_\-]+/, '_')
        str = str.replaceAll(/^[^a-zA-Z0-9]+/, '')
        str = str.replaceAll(/[^a-zA-Z0-9]+$/, '')
        return str.take(60)
    }

    static String filterStatus (NomadResponseJson resp, String taskName) {
        def statusMap = resp.json.Summary["${taskName}_taskgroup"]
        def status = statusMap.find{it.value == 1}.key
        return status
    }

    static TaskStatus mapJobToTaskStatus(NomadResponseJson resp, String taskName) {
        def parsedStatus = filterStatus(resp, taskName)

        def Map<String, TaskStatus> TASK_STATUS = [
            'Queued'           : TaskStatus.SUBMITTED,
            'Starting'         : TaskStatus.RUNNING,
            'Running'          : TaskStatus.RUNNING,
            'Complete'         : TaskStatus.COMPLETED,
            'Failed'           : TaskStatus.COMPLETED,
            'Lost'             : TaskStatus.COMPLETED,
            'Unknown'          : TaskStatus.COMPLETED,
        ]

        return TASK_STATUS[parsedStatus]
    }



}



//package nextflow.nomad.model
//
//import groovy.transform.CompileStatic
//import groovy.transform.EqualsAndHashCode
//import groovy.transform.ToString
//
//@CompileStatic
//@ToString(includeNames = true)
//@EqualsAndHashCode(includeFields = true)
//class NomadJobConstraints {
//
//    private Map spec = [:]
//
//    PodNodeSelector(selector) {
//        if( selector instanceof CharSequence )
//            createWithString(selector.toString())
//
//        else if( selector instanceof Map )
//            createWithMap(selector)
//
//        else if( selector != null )
//            throw new IllegalArgumentException("K8s invalid pod nodeSelector value: $selector [${selector.getClass().getName()}]")
//    }
//
//    private createWithMap(Map selection ) {
//        if(selection) {
//            for( Map.Entry entry : selection.entrySet() ) {
//                spec.put(entry.key.toString(), entry.value?.toString())
//            }
//        }
//    }
//
//    /**
//     * @param selector
//     *      A string representing a comma separated list of pairs
//     *      e.g. foo=1,bar=2
//     *
//     */
//    private createWithString( String selector ) {
//        if(!selector) return
//        def entries = selector.tokenize(',')
//        for( String item : entries ) {
//            def pair = item.tokenize('=')
//            spec.put( trim(pair[0]), trim(pair[1]) ?: 'true' )
//        }
//    }
//
//    private String trim(String v) {
//        v?.trim()
//    }
//
//    Map<String,String> toSpec() { spec }
//
//    String toString() {
//        "PodNodeSelector[ ${spec?.toString()} ]"
//    }
//}
//
//
//class NomadJobMeta {
//}
