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
 * Nomad Job Affinity Spec
 *
 * @author Jorge Aguilera <jagedn@gmail.com>
 */
class JobAffinity {

    private String attribute
    private String operator
    private String value
    private Integer weight

    String getOperator(){
        return operator
    }

    String getAttribute() {
        return attribute
    }

    String getValue() {
        return value
    }

    Integer getWeight() {
        return weight
    }

    JobAffinity attribute(String attribute){
        this.attribute=attribute
        this
    }

    JobAffinity operator(String operator){
        this.operator = operator
        this
    }

    JobAffinity value(String value){
        this.value = value
        this
    }

    JobAffinity weight(int weight){
        this.weight = weight
        this
    }

    void validate(){
    }
}