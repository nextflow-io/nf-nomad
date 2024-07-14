package nextflow.nomad.config

class ConstraintsSpec {

    List<ConstraintNodeSpec> nodeSpecs = []
    List<ConstraintAttrSpec> attrSpecs = []

    ConstraintsSpec node( @DelegatesTo(ConstraintNodeSpec)Closure closure){
        ConstraintNodeSpec constraintSpec = new ConstraintNodeSpec()
        def clone = closure.rehydrate(constraintSpec, closure.owner, closure.thisObject)
        clone.resolveStrategy = Closure.DELEGATE_FIRST
        clone()
        nodeSpecs << constraintSpec
        this
    }

    ConstraintsSpec attr( @DelegatesTo(ConstraintAttrSpec)Closure closure){
        ConstraintAttrSpec constraintSpec = new ConstraintAttrSpec()
        def clone = closure.rehydrate(constraintSpec, closure.owner, closure.thisObject)
        clone.resolveStrategy = Closure.DELEGATE_FIRST
        clone()
        attrSpecs << constraintSpec
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
