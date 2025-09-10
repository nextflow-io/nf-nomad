package nextflow.nomad.models

import groovy.transform.CompileStatic
import io.nomadproject.client.model.Spread
import io.nomadproject.client.model.SpreadTarget

/**
 * Nomad Job Spread Spec Builder
 *
 * @author Jorge Aguilera <jorge@edn.es>
 */

@CompileStatic
class SpreadsBuilder {

    static List<Spread> spreadsSpecToList(JobSpreads spreads){
        def ret = [] as List<Spread>

        spreads.raws.each{raw->
            def targets = [] as List<SpreadTarget>
            raw.right.each {
                targets.add( new SpreadTarget(value: it.left, percent: it.right) )
            }
            ret.add new Spread(attribute: raw.left, weight: raw.middle, spreadTarget: targets)
        }

        return ret
    }

}
