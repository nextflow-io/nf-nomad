/*
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
package nextflow.nomad.executor.spi

import groovy.transform.CompileStatic
import nextflow.executor.ScriptFileCopyStrategy
import nextflow.nomad.executor.NomadLifecycleTaskSpec

/**
 * Default fallback provider: reports {@link #isEnabled()} false and
 * exposes inert accessors. Used when no transfer-backend plugin is
 * active (i.e. the cluster has a shared filesystem and Nomad's vanilla
 * mode is sufficient).
 */
@CompileStatic
class NoopDistributedWorkdirProvider implements DistributedWorkdirProvider {

    @Override String name() { 'none' }
    @Override boolean isEnabled() { false }
    @Override boolean isExternallyStaged() { false }
    @Override void prepare() { /* no-op */ }
    @Override List<String> getSubmitCommand() { Collections.<String>emptyList() }
    @Override Map<String, String> getSubmitEnv() { Collections.<String,String>emptyMap() }
    @Override List<NomadLifecycleTaskSpec> getLifecycleTasks() { Collections.<NomadLifecycleTaskSpec>emptyList() }
    @Override ScriptFileCopyStrategy createCopyStrategy(boolean stagingDisabled) { null }
    @Override Integer synchronizeCompletion() { null }
    @Override String getRemoteExitHint() { null }
}
