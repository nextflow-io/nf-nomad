package nextflow.nomad.models

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
