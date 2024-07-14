package nextflow.nomad.executor

import io.nomadproject.client.model.Constraint
import nextflow.nomad.config.ConstraintAttrSpec
import nextflow.nomad.config.ConstraintNodeSpec
import nextflow.nomad.config.ConstraintsSpec

class ConstraintsBuilder {

    protected static List<Constraint> constraintsSpecToList(ConstraintsSpec spec){
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

    protected static List<Constraint> nodeConstraints(ConstraintNodeSpec nodeSpec){
        def ret = [] as List<Constraint>
        if( nodeSpec.id ){
            ret.add new Constraint()
                    .ltarget('${node.unique.id}')
                    .operand("=")
                    .rtarget(nodeSpec.id)
        }
        if( nodeSpec.name ){
            ret.add new Constraint()
                    .ltarget('${node.unique.name}')
                    .operand("=")
                    .rtarget(nodeSpec.name)
        }
        if( nodeSpec.clientClass ){
            ret.add new Constraint()
                    .ltarget('${node.class}')
                    .operand("=")
                    .rtarget(nodeSpec.clientClass)
        }
        if( nodeSpec.dataCenter ){
            ret.add new Constraint()
                    .ltarget('${node.datacenter}')
                    .operand("=")
                    .rtarget(nodeSpec.dataCenter)
        }
        if( nodeSpec.region ){
            ret.add new Constraint()
                    .ltarget('${node.region}')
                    .operand("=")
                    .rtarget(nodeSpec.region)
        }
        if( nodeSpec.pool ){
            ret.add new Constraint()
                    .ltarget('${node.pool}')
                    .operand("=")
                    .rtarget(nodeSpec.pool)
        }
        ret
    }

    protected static List<Constraint> attrConstraints(ConstraintAttrSpec nodeSpec) {
        def ret = [] as List<Constraint>
        if (nodeSpec.arch) {
            ret.add new Constraint()
                    .ltarget('${attr.cpu.arch}')
                    .operand("=")
                    .rtarget(nodeSpec.arch)
        }
        if (nodeSpec.numcores) {
            ret.add new Constraint()
                    .ltarget('${attr.cpu.numcores}')
                    .operand("=")
                    .rtarget("$nodeSpec.numcores")
        }
        if (nodeSpec.reservablecores) {
            ret.add new Constraint()
                    .ltarget('${attr.cpu.reservablecores}')
                    .operand("=")
                    .rtarget("$nodeSpec.reservablecores")
        }
        if (nodeSpec.totalcompute) {
            ret.add new Constraint()
                    .ltarget('${attr.cpu.totalcompute}')
                    .operand("=")
                    .rtarget("$nodeSpec.totalcompute")
        }
        ret
    }

}
