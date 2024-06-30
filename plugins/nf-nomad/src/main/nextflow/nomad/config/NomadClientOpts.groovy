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
class NomadClientOpts{

    final static protected API_VERSION = "v1"

    private Map<String,String> sysEnv

    final String address
    final String token

    NomadClientOpts(Map nomadClientOpts, Map<String,String> env=null){
        assert nomadClientOpts!=null

        sysEnv = env==null ? new HashMap<String,String>(System.getenv()) : env

        def tmp = (nomadClientOpts.address?.toString() ?: sysEnv.get('NOMAD_ADDR'))

        if( !tmp.endsWith("/"))
            tmp +="/"
        this.address = tmp + API_VERSION
        this.token = nomadClientOpts.token ?: sysEnv.get('NOMAD_TOKEN')

        //TODO: Add mTLS properties and env vars
        // https://developer.hashicorp.com/nomad/docs/commands#mtls-environment-variables
    }
}