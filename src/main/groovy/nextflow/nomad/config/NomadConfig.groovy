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
package nextflow.nomad.config

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j


/**
 * Nomad Config
 *
 * @author Jorge Aguilera <jagedn@gmail.com>
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */

@Slf4j
@CompileStatic
class NomadConfig {

    private NomadClientOpts clientOpts
    private NomadJobOpts jobOpts
    private NomadDebug debug

    NomadConfig(Map nomadConfigMap) {
        this.clientOpts = new NomadClientOpts((nomadConfigMap?.client ?: Collections.emptyMap()) as Map)
        this.jobOpts = new NomadJobOpts((nomadConfigMap?.jobs ?: Collections.emptyMap()) as Map)
        this.debug = new NomadDebug((nomadConfigMap?.debug ?:  Collections.emptyMap()) as Map)
    }


    NomadClientOpts clientOpts() { clientOpts }

    NomadJobOpts jobOpts() { jobOpts }

    NomadDebug debug() { debug }

    static class NomadDebug {

        @Delegate
        Map<String,Object> target

        NomadDebug(Map<String,Object> debug) {
            this.target = debug ?: Collections.<String,Object>emptyMap()
        }

        boolean getJson() { Boolean.valueOf( target.json as String ) }
    }
}
