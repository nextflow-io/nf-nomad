

/*
 * Copyright 2013-2023, Seqera Labs
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

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import nextflow.nomad.model.NomadJobOptions
import nextflow.util.Duration
import nextflow.Global
import nextflow.Session

import javax.annotation.Nullable

/**
 * Model Nomad specific settings defined in the nextflow
 * configuration file
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class NomadConfig {

    private NomadClientOpts clientOpts

    NomadClientOpts client() { clientOpts }

    NomadConfig(Map nomadConfigMap) {
        this.clientOpts = new NomadClientOpts((Map) nomadConfigMap.client ?: Collections.emptyMap())
    }

    static NomadConfig getConfig(Session session) {
        if (!session)
            throw new IllegalStateException("Missing Nextflow session")

        new NomadConfig((Map) session.config.nomad ?: Collections.emptyMap())
    }

    static NomadConfig getConfig() {
        getConfig(Global.session as Session)
    }

}

