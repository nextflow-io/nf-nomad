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
package nextflow.nomad

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.nomad.config.AffinitySpec
import nextflow.nomad.config.ConstraintSpec
import nextflow.nomad.config.VolumeSpec

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
        final VolumeSpec[] volumeSpec
        final AffinitySpec affinitySpec
        final ConstraintSpec constraintSpec

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

            this.volumeSpec = parseVolumes(nomadJobOpts)
            this.affinitySpec = parseAffinity(nomadJobOpts)
            this.constraintSpec = parseConstraint(nomadJobOpts)
        }

        VolumeSpec[] parseVolumes(Map nomadJobOpts){
            List<VolumeSpec> ret = []
            if( nomadJobOpts.volume && nomadJobOpts.volume instanceof Closure){
                def volumeSpec = new VolumeSpec()
                def closure = (nomadJobOpts.volume as Closure)
                def clone = closure.rehydrate(volumeSpec, closure.owner, closure.thisObject)
                clone.resolveStrategy = Closure.DELEGATE_FIRST
                clone()
                volumeSpec.workDir(true)
                ret.add volumeSpec
            }

            if( nomadJobOpts.volumes && nomadJobOpts.volumes instanceof List){
                nomadJobOpts.volumes.each{ closure ->
                    if( closure instanceof Closure){
                        def volumeSpec = new VolumeSpec()
                        def clone = closure.rehydrate(volumeSpec, closure.owner, closure.thisObject)
                        clone.resolveStrategy = Closure.DELEGATE_FIRST
                        clone()
                        ret.add volumeSpec
                    }
                }
            }

            if( ret.size() && !ret.find{ it.workDir } ){
                ret.first().workDir(true)
            }

            ret*.validate()

            if( ret.findAll{ it.workDir}.size() > 1 ){
                throw new IllegalArgumentException("No more than a workdir volume allowed")
            }

            return ret as VolumeSpec[]
        }

        AffinitySpec parseAffinity(Map nomadJobOpts) {
            if (nomadJobOpts.affinity && nomadJobOpts.affinity instanceof Closure) {
                def affinitySpec = new AffinitySpec()
                def closure = (nomadJobOpts.affinity as Closure)
                def clone = closure.rehydrate(affinitySpec, closure.owner, closure.thisObject)
                clone.resolveStrategy = Closure.DELEGATE_FIRST
                clone()
                affinitySpec.validate()
                affinitySpec
            } else {
                null
            }
        }

        ConstraintSpec parseConstraint(Map nomadJobOpts){
            if (nomadJobOpts.constraint && nomadJobOpts.constraint instanceof Closure) {
                def constraintSpec = new ConstraintSpec()
                def closure = (nomadJobOpts.constraint as Closure)
                def clone = closure.rehydrate(constraintSpec, closure.owner, closure.thisObject)
                clone.resolveStrategy = Closure.DELEGATE_FIRST
                clone()
                constraintSpec.validate()
                constraintSpec
            } else {
                null
            }
        }
    }

}
