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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Nomad Config
 *
 * @author Jorge Aguilera <jagedn@gmail.com>
 */

@Slf4j
@CompileStatic
class NomadConfig {
    final static protected API_VERSION = "v1"

    final NomadClientOpts clientOpts
    final NomadJobOpts jobOpts

    NomadConfig(Map nomadConfigMap) {
        clientOpts = new NomadClientOpts((nomadConfigMap?.client ?: Collections.emptyMap()) as Map)
        jobOpts = new NomadJobOpts((nomadConfigMap?.jobs ?: Collections.emptyMap()) as Map)
    }

    class NomadClientOpts{
        final String address
        final String token

        NomadClientOpts(Map nomadClientOpts){
            def tmp = (nomadClientOpts.address?.toString() ?: "http://127.0.0.1:4646")
            if( !tmp.endsWith("/"))
                tmp +="/"
            this.address = tmp + API_VERSION
            token = nomadClientOpts.token ?: null
        }
    }

    class NomadJobOpts{
        final boolean deleteOnCompletion
        final List<String> datacenters
        final String region
        final String namespace
        final String dockerVolume

        NomadJobOpts(Map nomadJobOpts){
            deleteOnCompletion = nomadJobOpts.containsKey("deleteOnCompletion") ?
                    nomadJobOpts.deleteOnCompletion : false
            if( nomadJobOpts.containsKey("datacenters") ) {
                datacenters = ((nomadJobOpts.datacenters instanceof List<String> ?
                        nomadJobOpts.datacenters : nomadJobOpts.datacenters.toString().split(","))
                        as List<String>).findAll{it.size()}.unique()
            }else{
                datacenters = []
            }
            region = nomadJobOpts.region ?: null
            namespace = nomadJobOpts.namespace ?: null
            dockerVolume = nomadJobOpts.dockerVolume ?: null
        }
    }
}
