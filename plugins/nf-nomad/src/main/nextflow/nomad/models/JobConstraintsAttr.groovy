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

class JobConstraintsAttr {

    private List<Triple<String, String, String>> raws= []

    List<Triple<String, String, String>> getRaws() {
        return raws
    }

    JobConstraintsAttr setCpu(Map map){
        cpu(map)
    }

    JobConstraintsAttr cpu(Map map){
        if( map.containsKey('arch'))
            raw("cpu.arch","=", map['arch'].toString())
        if( map.containsKey('numcores'))
            raw("cpu.numcores",">=", map['numcores'].toString())
        if( map.containsKey('reservablecores'))
            raw("cpu.reservablecores",">=", map['reservablecores'].toString())
        if( map.containsKey('totalcompute'))
            raw("cpu.totalcompute","=", map['totalcompute'].toString())
        this
    }

    JobConstraintsAttr setUnique(Map map){
        unique(map)
    }

    JobConstraintsAttr unique(Map map){
        if( map.containsKey('hostname'))
            raw("unique.hostname","=", map['hostname'].toString())
        if( map.containsKey('ip-address'))
            raw("unique.network.ip-address","=", map['ip-address'].toString())
        this
    }

    JobConstraintsAttr setKernel(Map map){
        kernel(map)
    }

    JobConstraintsAttr kernel(Map map){
        if( map.containsKey('arch'))
            raw("kernel.arch","=", map['arch'].toString())
        if( map.containsKey('name'))
            raw("kernel.name","=", map['name'].toString())
        if( map.containsKey('version'))
            raw("kernel.version","=", map['version'].toString())
        this
    }

    JobConstraintsAttr raw(String attr, String operator, String value){
        raws.add Triple.of("attr."+attr, operator, value)
        this
    }
}
