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

/**
 * Nomad Job Constraint Spec
 *
 * @author Jorge Aguilera <jagedn@gmail.com>
 */

class JobConstraintsAttr {

    private String arch = null
    private Integer numcores= null
    private Integer reservablecores= null
    private Double totalcompute= null

    String getArch() {
        return arch
    }

    Integer getNumcores() {
        return numcores
    }

    Integer getReservablecores() {
        return reservablecores
    }

    Double getTotalcompute() {
        return totalcompute
    }

    JobConstraintsAttr setCpu(Map map){
        cpu(map)
    }

    JobConstraintsAttr cpu(Map map){
        this.arch = map.containsKey("arch") ? map["arch"].toString() : null
        this.numcores = map.containsKey("numcores") ? map["numcores"] as int : null
        this.reservablecores = map.containsKey("reservablecores") ? map["reservablecores"] as int : null
        this.totalcompute = map.containsKey("totalcompute") ? map["totalcompute"] as double : null
        this
    }

}
