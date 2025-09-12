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


package nextflow.nomad.models

import org.apache.commons.lang3.tuple.Pair
import org.apache.commons.lang3.tuple.Triple

/**
 * Nomad Job Spread Spec
 *
 * @author Jorge Aguilera <jorge@edn.es>
 */

class JobSpreads {

    private List<Triple<String, Integer, List<Pair<String, Integer>>>> raws= []

    List<Triple<String, Integer, List<Pair<String, Integer>>>> getRaws() {
        return raws
    }

    JobSpreads setSpread(Map map){
        spread(map)
    }

    JobSpreads spread(Map map){
        if( map.containsKey("name") && map.containsKey("weight")){
            def name = map.name as String
            def weight = map.weight as int
            def targets = [] as List<Pair>
            if( map.containsKey("targets") && map.targets instanceof Map){
                (map.targets as Map).entrySet().each{entry->
                    def target = entry.key as String
                    if( entry.value.toString().isNumber() ){
                        def targetW = entry.value as int
                        targets.add( Pair.of(target, targetW))
                    }
                }
            }
            raws.add Triple.of(name, weight, targets)
        }
        this
    }

    void validate(){

    }
}
