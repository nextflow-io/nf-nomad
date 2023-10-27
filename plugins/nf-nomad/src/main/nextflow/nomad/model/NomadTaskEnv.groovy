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
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
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

    static NomadTaskEnv fieldPath(String env, String fieldPath) {
        new NomadTaskEnv([ name: env, valueFrom: [fieldRef:[fieldPath: fieldPath]]])
    }

    static NomadTaskEnv config(String env, String config) {
        final tokens = config.tokenize('/')
        if( tokens.size() > 2 )
            throw new IllegalArgumentException("Nomad invalid env: $config -- Secret must be specified as <config-name>/<config-key>")

        final name = tokens[0]
        final key = tokens[1]

        assert env, 'Missing task env variable name'
        assert name, 'Missing task env config name'

        final ref = [ name: name, key: (key ?: env) ]
        new NomadTaskEnv([ name: env, valueFrom: [configMapKeyRef: ref]])
    }

    Map toSpec() { spec }

    String toString() {
        "NomadTaskEnv[ ${spec?.toString()} ]"
    }
}
