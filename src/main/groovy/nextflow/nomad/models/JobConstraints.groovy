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

class JobConstraints {

    List<JobConstraintsNode> nodeSpecs = []
    List<JobConstraintsAttr> attrSpecs = []

    JobConstraints node(@DelegatesTo(JobConstraintsNode)Closure closure){
        JobConstraintsNode constraintSpec = new JobConstraintsNode()
        def clone = closure.rehydrate(constraintSpec, closure.owner, closure.thisObject)
        clone.resolveStrategy = Closure.DELEGATE_FIRST
        clone()
        nodeSpecs << constraintSpec
        this
    }

    JobConstraints attr(@DelegatesTo(JobConstraintsAttr)Closure closure){
        JobConstraintsAttr constraintSpec = new JobConstraintsAttr()
        def clone = closure.rehydrate(constraintSpec, closure.owner, closure.thisObject)
        clone.resolveStrategy = Closure.DELEGATE_FIRST
        clone()
        attrSpecs << constraintSpec
        this
    }

    void validate(){

    }

    static JobConstraints parse(@DelegatesTo(JobConstraints)Closure closure){
        JobConstraints constraintsSpec = new JobConstraints()
        def clone = closure.rehydrate(constraintsSpec, closure.owner, closure.thisObject)
        clone.resolveStrategy = Closure.DELEGATE_FIRST
        clone()
        constraintsSpec.validate()
        constraintsSpec
    }
}
