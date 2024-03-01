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

    final static public String VOLUME_DOCKER_TYPE = "docker"
    final static public String VOLUME_CSI_TYPE = "csi"
    final static public String VOLUME_HOST_TYPE = "host"

    final static protected String[] VOLUME_TYPES = [
            VOLUME_CSI_TYPE, VOLUME_DOCKER_TYPE, VOLUME_HOST_TYPE
    ]

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
        final VolumeSpec volumeSpec

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
            if( dockerVolume ){
                log.info "dockerVolume config will be deprecated, use volume type:'docker' name:'name' instead"
            }
            if( nomadJobOpts.volume && nomadJobOpts.volume instanceof Closure){
                this.volumeSpec = new VolumeSpec()
                def closure = (nomadJobOpts.volume as Closure)
                def clone = closure.rehydrate(this.volumeSpec, closure.owner, closure.thisObject)
                clone.resolveStrategy = Closure.DELEGATE_FIRST
                clone()
                this.volumeSpec.validate()
            }else{
                volumeSpec = null
            }
        }
    }

    class VolumeSpec{

        private String type
        private String name

        String getType() {
            return type
        }

        String getName() {
            return name
        }

        VolumeSpec type(String type){
            this.type = type
            this
        }

        VolumeSpec name(String name){
            this.name = name
            this
        }

        protected validate(){
            if( !VOLUME_TYPES.contains(type) ) {
                throw new IllegalArgumentException("Volume type $type is not supported")
            }
            if( !this.name ){
                throw new IllegalArgumentException("Volume name is required")
            }
        }
    }
}
