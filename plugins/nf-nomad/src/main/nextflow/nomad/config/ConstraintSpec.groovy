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
/**
 * Nomad Job Constraint Spec
 *
 * @author Jorge Aguilera <jagedn@gmail.com>
 */

class ConstraintSpec {

    private String attribute
    private String operator
    private String value

    String getOperator(){
        return operator
    }

    String getAttribute() {
        return attribute
    }

    String getValue() {
        return value
    }

    ConstraintSpec attribute(String attribute){
        this.attribute=attribute
        this
    }

    ConstraintSpec operator(String operator){
        this.operator = operator
        this
    }

    ConstraintSpec value(String value){
        this.value = value
        this
    }

    void validate(){
    }
}