/*
 * Copyright 2023-, Stellenbosch University, South Africa
 * Copyright 2024, Evaluacion y Desarrollo de Negocios, Spain
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
class NomadConstraintsSpec extends Specification {


    void "should instantiate a constraints spec if specified"() {
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: {
                            node  {
                                unique = [id :"node-id", name: "node-name"]
                                clientClass = "linux-64bit"
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
        config.jobOpts.constraintsSpec.nodeSpecs[0].id == "node-id"
        config.jobOpts.constraintsSpec.nodeSpecs[0].name == "node-name"
        config.jobOpts.constraintsSpec.nodeSpecs[0].clientClass == "linux-64bit"
        config.jobOpts.constraintsSpec.nodeSpecs[0].pool == "custom-pool"
        config.jobOpts.constraintsSpec.nodeSpecs[0].dataCenter == "dc1"
        config.jobOpts.constraintsSpec.nodeSpecs[0].region == "us"

        config.jobOpts.constraintsSpec.attrSpecs.size() == 1
        config.jobOpts.constraintsSpec.attrSpecs[0].arch == '286'
    }

    void "should instantiate a no completed constraints spec"() {
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: {
                            node  {
                                unique = [id :"node-id", name: "node-name"]
                                clientClass = "linux-64bit"
                            }
                        }
                ]
        ])

        then:
        config.jobOpts.constraintsSpec
        config.jobOpts.constraintsSpec.nodeSpecs.size()
        config.jobOpts.constraintsSpec.nodeSpecs[0].id == "node-id"
        config.jobOpts.constraintsSpec.nodeSpecs[0].name == "node-name"
        config.jobOpts.constraintsSpec.nodeSpecs[0].clientClass == "linux-64bit"
        !config.jobOpts.constraintsSpec.nodeSpecs[0].pool
        !config.jobOpts.constraintsSpec.nodeSpecs[0].dataCenter
    }

    void "should instantiate a list of constraints spec if specified"() {
        when:
        def config = new NomadConfig([
                jobs: [
                        constraints: {
                            node  {
                                unique = [id :"node-id", name: "node-name"]
                                clientClass = "linux-64bit"
                                pool = "custom-pool"
                                dataCenter = 'dc1'
                                region = 'us'
                            }
                            node  {
                                unique = [id :"node-id", name: "node-name"]
                                clientClass = "linux-64bit"
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
        config.jobOpts.constraintsSpec.nodeSpecs[0].id == "node-id"
        config.jobOpts.constraintsSpec.nodeSpecs[0].name == "node-name"
        config.jobOpts.constraintsSpec.nodeSpecs[0].clientClass == "linux-64bit"
        config.jobOpts.constraintsSpec.nodeSpecs[0].pool == "custom-pool"
        config.jobOpts.constraintsSpec.nodeSpecs[0].dataCenter == "dc1"
        config.jobOpts.constraintsSpec.nodeSpecs[0].region == "us"
    }
}
