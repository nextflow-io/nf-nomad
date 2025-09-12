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

import org.apache.commons.lang3.tuple.Triple

/**
 * Nomad Job Constraint Spec
 *
 * @author Jorge Aguilera <jagedn@gmail.com>
 */

class JobConstraintsNode {

    private List<Triple<String, String, String>> raws= []

    List<Triple<String, String, String>> getRaws() {
        return raws
    }

    JobConstraintsNode setUnique(Map map){
        unique(map)
    }

    JobConstraintsNode unique(Map map){
        ['id', 'name'].each { key->
            if( map.containsKey(key))
                raw("unique.${key}","=", map[key].toString())
        }
        this
    }

    JobConstraintsNode setClazz(Object map){ // class is a reserved word, in java we used clazz
        clazz(map)
    }

    JobConstraintsNode clazz(Object cls){
        raw("class","=", cls.toString())
        this
    }

    JobConstraintsNode setPool(Object map){
        pool(map)
    }

    JobConstraintsNode pool(Object pool){
        raw("pool","=", pool.toString())
        this
    }

    JobConstraintsNode setDataCenter(Object map){
        dataCenter(map)
    }

    JobConstraintsNode dataCenter(Object dataCenter){
        raw("datacenter","=", dataCenter.toString())
        this
    }

    JobConstraintsNode setRegion(Object map){
        region(map)
    }

    JobConstraintsNode region(Object region){
        raw("region","=", region.toString())
        this
    }

    JobConstraintsNode raw(String attr, String operator, String value){
        raws.add Triple.of("node."+attr, operator, value)
        this
    }
}
