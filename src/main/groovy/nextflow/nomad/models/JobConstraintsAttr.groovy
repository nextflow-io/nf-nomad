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
class JobConstraintsAttr {

    private static final Set<String> KNOWN_KEYS = ['cpu', 'unique', 'kernel'] as Set
    private static final Set<String> KNOWN_CPU_KEYS =
            ['arch', 'numcores', 'reservablecores', 'totalcompute'] as Set
    private static final Set<String> KNOWN_UNIQUE_KEYS = ['hostname', 'ip-address'] as Set
    private static final Set<String> KNOWN_KERNEL_KEYS = ['arch', 'name', 'version'] as Set

    private List<Triple<String, String, String>> raws= []

    List<Triple<String, String, String>> getRaws() {
        return raws
    }

    JobConstraintsAttr setCpu(Map map){
        cpu(map)
    }

    JobConstraintsAttr cpu(Map map){
        if( map.containsKey('arch'))
            raw("cpu.arch","=", map['arch'].toString())
        if( map.containsKey('numcores'))
            raw("cpu.numcores",">=", map['numcores'].toString())
        if( map.containsKey('reservablecores'))
            raw("cpu.reservablecores",">=", map['reservablecores'].toString())
        if( map.containsKey('totalcompute'))
            raw("cpu.totalcompute","=", map['totalcompute'].toString())
        this
    }

    JobConstraintsAttr setUnique(Map map){
        unique(map)
    }

    JobConstraintsAttr unique(Map map){
        if( map.containsKey('hostname'))
            raw("unique.hostname","=", map['hostname'].toString())
        if( map.containsKey('ip-address'))
            raw("unique.network.ip-address","=", map['ip-address'].toString())
        this
    }

    JobConstraintsAttr setKernel(Map map){
        kernel(map)
    }

    JobConstraintsAttr kernel(Map map){
        if( map.containsKey('arch'))
            raw("kernel.arch","=", map['arch'].toString())
        if( map.containsKey('name'))
            raw("kernel.name","=", map['name'].toString())
        if( map.containsKey('version'))
            raw("kernel.version","=", map['version'].toString())
        this
    }

    JobConstraintsAttr raw(String attr, String operator, String value){
        raws.add Triple.of("attr."+attr, operator, value)
        this
    }

    /**
     * Build a {@link JobConstraintsAttr} from a plain Map (the form produced by
     * Nextflow's config-file parser when {@code attr {{ ... }}} appears inside a
     * {@code constraints {{ ... }}} block).
     */
    static JobConstraintsAttr fromMap(Map map){
        def spec = new JobConstraintsAttr()
        if( map == null ) {
            log.warn "Ignoring null nomad attr constraint Map"
            return spec
        }

        def unknown = map.keySet().findAll { !(KNOWN_KEYS.contains(it as String)) }
        if( unknown ) {
            log.warn "Unknown nomad attr constraint key(s) ${unknown} — supported: ${KNOWN_KEYS}"
        }

        applySubMap(spec, map, 'cpu',    KNOWN_CPU_KEYS,    { Map m -> spec.cpu(m) })
        applySubMap(spec, map, 'unique', KNOWN_UNIQUE_KEYS, { Map m -> spec.unique(m) })
        applySubMap(spec, map, 'kernel', KNOWN_KERNEL_KEYS, { Map m -> spec.kernel(m) })

        if( spec.raws.isEmpty() ) {
            log.warn "nomad attr constraint produced no raw entries — block has no effect (input keys: ${map.keySet()})"
        }
        spec
    }

    private static void applySubMap(JobConstraintsAttr spec, Map parent, String key,
                                    Set<String> known, Closure apply){
        if( !parent.containsKey(key) ) return
        def value = parent.get(key)
        if( !(value instanceof Map) ) {
            log.warn "nomad attr.${key} must be a Map, got ${value?.getClass()?.name}"
            return
        }
        Map m = value as Map
        def unknown = m.keySet().findAll { !(known.contains(it as String)) }
        if( unknown ) {
            log.warn "Unknown nomad attr.${key} key(s) ${unknown} — supported: ${known}"
        }
        if( !known.any { m.containsKey(it) } ) {
            log.warn "nomad attr.${key} map must contain at least one of ${known}"
            return
        }
        apply(m)
    }
}
