package nextflow.nomad.models

import io.nomadproject.client.model.Constraint
import nextflow.nomad.models.JobConstraintsAttr
import nextflow.nomad.models.JobConstraintsNode
import nextflow.nomad.models.JobConstraints

class ConstraintsBuilder {

    static List<Constraint> constraintsSpecToList(JobConstraints spec){
        def constraints = [] as List<Constraint>
        if( spec?.nodeSpecs ){
            def nodes = spec.nodeSpecs
                    ?.collect({ nodeConstraints(it)})
                    ?.flatten() as List<Constraint>
            constraints.addAll(nodes)
        }
        if( spec?.attrSpecs ){
            def nodes = spec.attrSpecs
                    ?.collect({ attrConstraints(it)})
                    ?.flatten() as List<Constraint>
            constraints.addAll(nodes)
        }
        return constraints
    }

    protected static List<Constraint> nodeConstraints(JobConstraintsNode nodeSpec){
        def ret = nodeSpec.raws?.collect{ triple->
            return new Constraint()
                    .ltarget('${'+triple.left+'}')
                    .operand(triple.middle)
                    .rtarget(triple.right)
        } as List<Constraint>
        ret
    }

    protected static List<Constraint> attrConstraints(JobConstraintsAttr nodeSpec) {
        def ret = nodeSpec.raws?.collect{ triple->
            return new Constraint()
                    .ltarget('${'+triple.left+'}')
                    .operand(triple.middle)
                    .rtarget(triple.right)
        } as List<Constraint>
        ret
    }

}
