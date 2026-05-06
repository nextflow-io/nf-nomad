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

import nextflow.processor.TaskRun
import org.pf4j.ExtensionPoint

import java.nio.file.Path

/**
 * PF4J extension point that lets transfer-tool plugins register their
 * {@link DistributedWorkdirProvider} implementation with
 * {@code nf-nomad}. Discovered at runtime via
 * {@code Plugins.getExtensions(DistributedWorkdirProviderFactory.class)} —
 * which traverses every loaded plugin's classloader, so this works across
 * PF4J's plugin-classloader isolation (unlike a raw {@code Class.forName}).
 *
 * <p>To register a provider from inside another plugin:</p>
 * <ol>
 *   <li>Add {@code requirePlugins = ['nf-nomad']} to the
 *       {@code nextflowPlugin {}} block in your build.gradle so PF4J wires
 *       up the parent classloader (Plugin-Dependencies in the manifest).</li>
 *   <li>Add a {@code compileOnly} dep on nf-nomad so this interface is on
 *       your compile classpath.</li>
 *   <li>Implement this interface, annotate with {@code @org.pf4j.Extension},
 *       and add the FQN to your plugin's {@code extensionPoints} list.</li>
 * </ol>
 */
interface DistributedWorkdirProviderFactory extends ExtensionPoint {

    /** Provider id used in log messages and error hints. */
    String name()

    /**
     * Fast pre-check: does this provider want to handle distributed workdir
     * for the current session? Should not allocate a per-task interop —
     * that's {@link #create}. Driven entirely by the session config block
     * the operator supplied; concrete provider plugins decide which scope
     * key(s) and flags they read.
     */
    boolean isEnabled(Map sessionConfig)

    /**
     * Construct a per-task provider instance. Called once per task during
     * NomadTaskHandler construction, after {@link #isEnabled} returns true.
     */
    DistributedWorkdirProvider create(TaskRun task, Map sessionConfig, Path sessionWorkDir)
}
