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

package nextflow.nomad.util

import spock.lang.Specification
import spock.lang.Unroll

class NomadLoggingSpec extends Specification {

    @Unroll
    void "should resolve NF_NOMAD_DEBUG value '#value' to level #expected"() {
        expect:
        NomadLogging.resolveDebugLevel(value) == expected

        where:
        value      | expected
        null       | 0
        ''         | 0
        '   '      | 0
        '1'        | 1
        '2'        | 2
        'debug'    | 1
        'trace'    | 2
        'DEBUG'    | 1
        'TRACE'    | 2
        'invalid'  | 0
    }

    @Unroll
    void "should derive debug/trace flags from NF_NOMAD_DEBUG value '#value'"() {
        given:
        int level = NomadLogging.resolveDebugLevel(value)

        expect:
        (level >= 1) == debugEnabled
        (level >= 2) == traceEnabled

        where:
        value      | debugEnabled | traceEnabled
        null       | false        | false
        '0'        | false        | false
        '1'        | true         | false
        '2'        | true         | true
        'trace'    | true         | true
        'debug'    | true         | false
        'invalid'  | false        | false
    }
}
