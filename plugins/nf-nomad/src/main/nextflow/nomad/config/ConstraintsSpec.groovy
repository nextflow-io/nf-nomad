package nextflow.nomad.config

class ConstraintsSpec {

    List<ConstraintNodeSpec> nodeSpecs = []

    ConstraintsSpec node( @DelegatesTo(ConstraintNodeSpec)Closure closure){
        ConstraintNodeSpec constraintNodeSpec = new ConstraintNodeSpec()
        def clone = closure.rehydrate(constraintNodeSpec, closure.owner, closure.thisObject)
        clone.resolveStrategy = Closure.DELEGATE_FIRST
        clone()
        nodeSpecs << constraintNodeSpec
        this
    }

    void validate(){

    }

    static ConstraintsSpec parse(@DelegatesTo(ConstraintsSpec)Closure closure){
        ConstraintsSpec constraintsSpec = new ConstraintsSpec()
        def clone = closure.rehydrate(constraintsSpec, closure.owner, closure.thisObject)
        clone.resolveStrategy = Closure.DELEGATE_FIRST
        clone()
        constraintsSpec.validate()
        constraintsSpec
    }
}
