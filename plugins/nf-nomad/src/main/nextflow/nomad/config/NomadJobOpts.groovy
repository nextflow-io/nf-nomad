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
 * Nomad JobOpts
 *
 * @author Jorge Aguilera <jagedn@gmail.com>
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Slf4j
@CompileStatic
class NomadJobOpts{
    private Map<String,String> sysEnv

    boolean deleteOnCompletion
    List<String> datacenters
    String region
    String namespace
    String dockerVolume
    VolumeSpec[] volumeSpec
    AffinitySpec affinitySpec
    ConstraintSpec constraintSpec

    NomadJobOpts(Map nomadJobOpts, Map<String,String> env=null){
        assert nomadJobOpts!=null

        sysEnv = env ?: new HashMap<String,String>(System.getenv())

        deleteOnCompletion = nomadJobOpts.containsKey("deleteOnCompletion") ?
                nomadJobOpts.deleteOnCompletion : false
        if( nomadJobOpts.containsKey("datacenters") ) {
            datacenters = ((nomadJobOpts.datacenters instanceof List<String> ?
                    nomadJobOpts.datacenters : nomadJobOpts.datacenters.toString().split(","))
                    as List<String>).findAll{it.size()}.unique()
        }else{
            datacenters = (sysEnv.get('NOMAD_DC')?:"").split(",") as List<String>
        }

        region = nomadJobOpts.region ?: sysEnv.get('NOMAD_REGION')
        namespace = nomadJobOpts.namespace ?: sysEnv.get('NOMAD_NAMESPACE')

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