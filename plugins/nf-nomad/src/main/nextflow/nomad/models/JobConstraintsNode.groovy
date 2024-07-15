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

class JobConstraintsNode {

    private String id = null
    private String name = null
    private String clientClass = null
    private String pool = null
    private String dataCenter = null
    private String region = null

    String getId() {
        return id
    }

    String getName() {
        return name
    }

    String getClientClass() {
        return clientClass
    }

    String getPool() {
        return pool
    }

    String getDataCenter() {
        return dataCenter
    }

    String getRegion() {
        return region
    }

    JobConstraintsNode setUnique(Map map){
        unique(map)
    }

    JobConstraintsNode unique(Map map){
        this.id = map.containsKey("id") ? map["id"].toString() : null
        this.name = map.containsKey("name") ? map["name"].toString() : null
        this
    }

    JobConstraintsNode setClientClass(Object map){
        clientClass(map)
    }

    JobConstraintsNode clientClass(Object clientClass){
        this.clientClass = clientClass.toString()
        this
    }

    JobConstraintsNode setPool(Object map){
        pool(map)
    }

    JobConstraintsNode pool(Object pool){
        this.pool = pool.toString()
        this
    }

    JobConstraintsNode setDataCenter(Object map){
        dataCenter(map)
    }

    JobConstraintsNode dataCenter(Object dataCenter){
        this.dataCenter = dataCenter.toString()
        this
    }

    JobConstraintsNode setRegion(Object map){
        region(map)
    }

    JobConstraintsNode region(Object region){
        this.region = region.toString()
        this
    }
}
