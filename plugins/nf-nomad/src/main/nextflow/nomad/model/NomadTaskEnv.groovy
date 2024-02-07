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

package nextflow.nomad.model

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Model a Nomad task environment variable definition
 * https://developer.hashicorp.com/nomad/docs/job-specification/env
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@CompileStatic
@ToString(includeNames = true)
@EqualsAndHashCode(includeFields = true)
class NomadTaskEnv {

    private Map spec

    private NomadTaskEnv(Map spec) {
        this.spec = spec
    }

    static NomadTaskEnv value(String env, String value) {
        new NomadTaskEnv([name:env, value:value])
    }

    Map toSpec() { spec }

    String toString() {
        "NomadTaskEnv[ ${spec?.toString()} ]"
    }
}
