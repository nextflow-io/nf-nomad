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

package nextflow.nomad.config

import spock.lang.Specification

/**
 * Unit test for Nomad Config
 *
 * @author : Jorge Aguilera <jagedn@gmail.com>
 * @author : Abhinav Sharma <abhi18av@outlook.com>
 */
class NomadJobConstraintsSpec extends Specification {


    void "should instantiate a constraints spec if specified"() {
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: {
                            node  {
                                unique = [id :"node-id", name: "node-name"]
                                clazz = "linux-64bit"
                                pool = "custom-pool"
                                dataCenter = 'dc1'
                                region = 'us'
                            }
                            attr{
                                cpu = [arch:'286']
                            }
                        }
                ]
        ])

        then:
        config.jobOpts.constraintsSpec
        config.jobOpts.constraintsSpec.nodeSpecs.size() == 1
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("id")}.right == "node-id"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("name")}.right == "node-name"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("class")}.right == "linux-64bit"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("pool")}.right == "custom-pool"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("datacenter")}.right == "dc1"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("region")}.right == "us"

        config.jobOpts.constraintsSpec.attrSpecs.size() == 1
        config.jobOpts.constraintsSpec.attrSpecs[0].raws[0].right == '286'
    }

    void "should instantiate a no completed constraints spec"() {
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: {
                            node  {
                                unique = [id :"node-id", name: "node-name"]
                                clazz = "linux-64bit"
                            }
                        }
                ]
        ])

        then:
        config.jobOpts.constraintsSpec
        config.jobOpts.constraintsSpec.nodeSpecs.size()
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("id")}.right == "node-id"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("name")}.right == "node-name"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("class")}.right == "linux-64bit"
        !config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("pool")}
        !config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("datacenter")}
    }

    void "should instantiate a list of constraints spec if specified"() {
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: {
                            node  {
                                unique = [id :"node-id", name: "node-name"]
                                clazz = "linux-64bit"
                                pool = "custom-pool"
                                dataCenter = 'dc1'
                                region = 'us'
                            }
                            node  {
                                unique = [id :"node-id", name: "node-name"]
                                clazz = "linux-64bit"
                                pool = "custom-pool"
                                dataCenter = 'dc1'
                                region = 'us'
                            }
                        }
                ]
        ])

        then:
        config.jobOpts.constraintsSpec
        config.jobOpts.constraintsSpec.nodeSpecs.size()
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("id")}.right == "node-id"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("name")}.right == "node-name"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("class")}.right == "linux-64bit"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("pool")}.right == "custom-pool"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("datacenter")}.right == "dc1"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find{it.left.endsWith("region")}.right == "us"
    }

    // ── Map-shape parsing ────────────────────────────────────────────────────
    //
    // Nextflow's config-file parser flattens block-form `constraints { node { ... } }`
    // into a Map of Maps. Previously this Map was silently ignored because the
    // Closure-only check failed; these specs lock in the Map-shape parser.

    void "should accept constraints supplied as a Map (config-file form)"() {
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: [
                                node: [
                                        unique    : [id: "node-id", name: "node-name"],
                                        clazz     : "linux-64bit",
                                        pool      : "custom-pool",
                                        dataCenter: "dc1",
                                        region    : "us",
                                ],
                                attr: [
                                        cpu: [arch: "286"],
                                ],
                        ]
                ]
        ])

        then:
        config.jobOpts.constraintsSpec
        config.jobOpts.constraintsSpec.nodeSpecs.size() == 1
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find { it.left.endsWith("id") }.right == "node-id"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find { it.left.endsWith("name") }.right == "node-name"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find { it.left.endsWith("class") }.right == "linux-64bit"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find { it.left.endsWith("pool") }.right == "custom-pool"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find { it.left.endsWith("datacenter") }.right == "dc1"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find { it.left.endsWith("region") }.right == "us"

        config.jobOpts.constraintsSpec.attrSpecs.size() == 1
        config.jobOpts.constraintsSpec.attrSpecs[0].raws[0].right == "286"
    }

    void "should accept Map shape with multiple node specs as a List"() {
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: [
                                node: [
                                        [unique: [name: "node-a"]],
                                        [unique: [name: "node-b"]],
                                ]
                        ]
                ]
        ])

        then:
        config.jobOpts.constraintsSpec
        config.jobOpts.constraintsSpec.nodeSpecs.size() == 2
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find { it.left.endsWith("name") }.right == "node-a"
        config.jobOpts.constraintsSpec.nodeSpecs[1].raws.find { it.left.endsWith("name") }.right == "node-b"
    }

    // ── Map-shape validations ────────────────────────────────────────────────

    void "should ignore an unknown top-level constraints key but still parse known ones"() {
        // `nodes:` (plural typo) should be ignored, `node:` still parsed.
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: [
                                nodes: [unique: [name: "ignored-typo"]],
                                node : [unique: [name: "kept"]],
                        ]
                ]
        ])

        then:
        config.jobOpts.constraintsSpec.nodeSpecs.size() == 1
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws
                .find { it.left.endsWith("name") }.right == "kept"
    }

    void "should ignore an unknown node key but still parse known ones"() {
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: [
                                node: [
                                        hostname: "wrong-key", // typo: belongs under attr.unique
                                        pool    : "custom-pool",
                                ]
                        ]
                ]
        ])

        then:
        // Only the known `pool` key survives; the typo is dropped.
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.size() == 1
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws[0].left == "node.pool"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws[0].right == "custom-pool"
    }

    void "should ignore an unknown attr.cpu sub-key but still parse known ones"() {
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: [
                                attr: [
                                        cpu: [
                                                arch    : "amd64",
                                                vendorId: "intel", // unsupported
                                        ]
                                ]
                        ]
                ]
        ])

        then:
        config.jobOpts.constraintsSpec.attrSpecs[0].raws.size() == 1
        config.jobOpts.constraintsSpec.attrSpecs[0].raws[0].left == "attr.cpu.arch"
    }

    void "should reject a non-Map node value gracefully (no exception)"() {
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: [node: "nomad02"]   // wrong shape
                ]
        ])

        then:
        // No exception, no specs, but the constraintsSpec exists so callers can introspect.
        config.jobOpts.constraintsSpec.nodeSpecs.isEmpty()
        config.jobOpts.constraintsSpec.attrSpecs.isEmpty()
    }

    void "should reject a non-Map node.unique value gracefully"() {
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: [
                                node: [
                                        unique: "nomad02", // should be [name: 'nomad02']
                                        pool  : "custom-pool",
                                ]
                        ]
                ]
        ])

        then:
        // unique is dropped, pool survives.
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.size() == 1
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws[0].left == "node.pool"
    }

    void "should accept an empty unique map with at least one valid sibling key"() {
        // unique = [:] alone produces nothing useful, but pool is still parsed.
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: [
                                node: [
                                        unique: [:],
                                        pool  : "custom-pool",
                                ]
                        ]
                ]
        ])

        then:
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.size() == 1
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws[0].left == "node.pool"
    }

    void "should accept the legacy 'class' and 'datacenter' Map keys"() {
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: [
                                node: [
                                        'class'   : "linux-64bit",
                                        datacenter: "dc1",
                                ]
                        ]
                ]
        ])

        then:
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find { it.left.endsWith("class") }.right == "linux-64bit"
        config.jobOpts.constraintsSpec.nodeSpecs[0].raws.find { it.left.endsWith("datacenter") }.right == "dc1"
    }
}
