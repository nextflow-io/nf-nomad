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
import org.apache.commons.lang3.tuple.Triple

/**
 * Nomad Job Constraint Spec
 *
 * @author Jorge Aguilera <jagedn@gmail.com>
 */

@Slf4j
class JobConstraintsNode {

    /** Valid top-level keys when constructing from a Map. */
    private static final Set<String> KNOWN_KEYS =
            ['unique', 'clazz', 'class', 'pool', 'dataCenter', 'datacenter', 'region'] as Set

    /** Valid keys inside the {@code unique} sub-map. */
    private static final Set<String> KNOWN_UNIQUE_KEYS = ['id', 'name'] as Set

    private List<Triple<String, String, String>> raws= []

    List<Triple<String, String, String>> getRaws() {
        return raws
    }

    JobConstraintsNode setUnique(Map map){
        unique(map)
    }

    JobConstraintsNode unique(Map map){
        ['id', 'name'].each { key->
            if( map.containsKey(key))
                raw("unique.${key}","=", map[key].toString())
        }
        this
    }

    JobConstraintsNode setClazz(Object map){ // class is a reserved word, in java we used clazz
        clazz(map)
    }

    JobConstraintsNode clazz(Object cls){
        raw("class","=", cls.toString())
        this
    }

    JobConstraintsNode setPool(Object map){
        pool(map)
    }

    JobConstraintsNode pool(Object pool){
        raw("pool","=", pool.toString())
        this
    }

    JobConstraintsNode setDataCenter(Object map){
        dataCenter(map)
    }

    JobConstraintsNode dataCenter(Object dataCenter){
        raw("datacenter","=", dataCenter.toString())
        this
    }

    JobConstraintsNode setRegion(Object map){
        region(map)
    }

    JobConstraintsNode region(Object region){
        raw("region","=", region.toString())
        this
    }

    JobConstraintsNode raw(String attr, String operator, String value){
        raws.add Triple.of("node."+attr, operator, value)
        this
    }

    /**
     * Build a {@link JobConstraintsNode} from a plain Map. This is the form Nextflow's
     * config-file DSL produces when users write {@code node {{ ... }}} inside a
     * {@code constraints {{ ... }}} block — the closure is flattened into a Map,
     * which previously failed the Closure-only parser silently.
     *
     * Accepts {@code class} as well as {@code clazz}, and {@code datacenter} as well
     * as {@code dataCenter}, to match both Groovy DSL and YAML/JSON style configs.
     */
    static JobConstraintsNode fromMap(Map map){
        def spec = new JobConstraintsNode()
        if( map == null ) {
            log.warn "Ignoring null nomad node constraint Map"
            return spec
        }

        // Warn on typos / unsupported keys so silent drops surface to the user.
        def unknown = map.keySet().findAll { !(KNOWN_KEYS.contains(it as String)) }
        if( unknown ) {
            log.warn "Unknown nomad node constraint key(s) ${unknown} — supported: ${KNOWN_KEYS - ['clazz', 'datacenter']}"
        }

        if( map.containsKey('unique') ) {
            if( map.unique instanceof Map ) {
                Map u = map.unique as Map
                def unknownUnique = u.keySet().findAll { !(KNOWN_UNIQUE_KEYS.contains(it as String)) }
                if( unknownUnique ) {
                    log.warn "Unknown nomad node.unique key(s) ${unknownUnique} — supported: ${KNOWN_UNIQUE_KEYS}"
                }
                if( !KNOWN_UNIQUE_KEYS.any { u.containsKey(it) } ) {
                    log.warn "nomad node.unique map must contain at least one of ${KNOWN_UNIQUE_KEYS}"
                }
                spec.unique(u)
            } else {
                log.warn "nomad node.unique must be a Map, got ${map.unique?.getClass()?.name}"
            }
        }

        if( map.containsKey('clazz') )            spec.clazz(map.clazz)
        else if( map.containsKey('class') )       spec.clazz(map['class'])
        if( map.containsKey('pool') )             spec.pool(map.pool)
        if( map.containsKey('dataCenter') )       spec.dataCenter(map.dataCenter)
        else if( map.containsKey('datacenter') )  spec.dataCenter(map.datacenter)
        if( map.containsKey('region') )           spec.region(map.region)

        if( spec.raws.isEmpty() ) {
            log.warn "nomad node constraint produced no raw entries — block has no effect (input keys: ${map.keySet()})"
        }
        spec
    }
}
