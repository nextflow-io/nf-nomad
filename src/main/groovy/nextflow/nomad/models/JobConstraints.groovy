/*
 * Copyright 2023-, Stellenbosch University, South Africa
 * Copyright 2024, Evaluacion y Desarrollo de Negocios, Spain
 * Copyright 2026-, Incremental Steps Software Solutions OÜ
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

import groovy.util.logging.Slf4j

/**
 * Nomad Job Constraint Spec
 *
 * @author Jorge Aguilera <jagedn@gmail.com>
 */

@Slf4j
class JobConstraints {

    private static final Set<String> KNOWN_KEYS = ['node', 'attr'] as Set

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

    /**
     * Build a {@link JobConstraints} from a Map. This handles the shape produced
     * by Nextflow's config-file parser when users write
     *
     * <pre>
     * nomad {{ jobs {{ constraints {{ node {{ unique = [name: 'host'] }} }} }} }}
     * </pre>
     *
     * which is parsed as a Map of Maps, not a Closure. Both single-block and
     * list-of-blocks shapes are accepted for {@code node} / {@code attr} so users
     * can express multiple specs even via Map config.
     *
     * Returns {@code null} when the supplied map is {@code null} or empty.
     */
    static JobConstraints fromMap(Map map){
        if( !map ) return null

        // Surface typos like `nodes:` (plural) or `attribute:` that would otherwise
        // be silently ignored by the addNodeSpecs / addAttrSpecs lookups.
        def unknown = map.keySet().findAll { !(KNOWN_KEYS.contains(it as String)) }
        if( unknown ) {
            log.warn "Unknown nomad constraints key(s) ${unknown} — supported: ${KNOWN_KEYS}"
        }

        JobConstraints spec = new JobConstraints()
        addNodeSpecs(spec, map.node)
        addAttrSpecs(spec, map.attr)
        spec.validate()

        if( spec.nodeSpecs.isEmpty() && spec.attrSpecs.isEmpty() ) {
            log.warn "nomad constraints block produced no specs — input keys: ${map.keySet()}"
        }
        spec
    }

    private static void addNodeSpecs(JobConstraints spec, Object value){
        if( value == null ) return
        if( value instanceof Map ) {
            spec.nodeSpecs << JobConstraintsNode.fromMap(value as Map)
        } else if( value instanceof Iterable ) {
            for( Object item : (value as Iterable) ) {
                if( item instanceof Map ) {
                    spec.nodeSpecs << JobConstraintsNode.fromMap(item as Map)
                } else {
                    log.warn "nomad constraints.node list entry must be a Map, got ${item?.getClass()?.name}"
                }
            }
        } else {
            log.warn "nomad constraints.node must be a Map or List<Map>, got ${value.getClass().name}"
        }
    }

    private static void addAttrSpecs(JobConstraints spec, Object value){
        if( value == null ) return
        if( value instanceof Map ) {
            spec.attrSpecs << JobConstraintsAttr.fromMap(value as Map)
        } else if( value instanceof Iterable ) {
            for( Object item : (value as Iterable) ) {
                if( item instanceof Map ) {
                    spec.attrSpecs << JobConstraintsAttr.fromMap(item as Map)
                } else {
                    log.warn "nomad constraints.attr list entry must be a Map, got ${item?.getClass()?.name}"
                }
            }
        } else {
            log.warn "nomad constraints.attr must be a Map or List<Map>, got ${value.getClass().name}"
        }
    }
}
