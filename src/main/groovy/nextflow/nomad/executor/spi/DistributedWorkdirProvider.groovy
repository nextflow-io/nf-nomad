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

import nextflow.executor.ScriptFileCopyStrategy
import nextflow.nomad.executor.NomadLifecycleTaskSpec

/**
 * Per-task abstraction over a distributed-workdir backend. Lets
 * {@link nextflow.nomad.executor.NomadTaskHandler} stay
 * agnostic of the actual data-transfer tool used to ship a task to a
 * Nomad client that does not share the operator's filesystem.
 *
 * <p>Lifecycle (called by {@code NomadTaskHandler}):</p>
 * <ol>
 *   <li>{@link #isEnabled()} — fast check; if false, the handler skips this provider entirely.</li>
 *   <li>{@link #createCopyStrategy(boolean)} — used by the bash wrapper builder.</li>
 *   <li>{@link #prepare()} — pre-submit setup (uploads scripts, builds submission).</li>
 *   <li>{@link #getSubmitCommand()} / {@link #getSubmitEnv()} / {@link #getLifecycleTasks()} —
 *       consumed when constructing the Nomad job.</li>
 *   <li>{@link #synchronizeCompletion()} — post-completion sync; returns the remote exit code.</li>
 * </ol>
 *
 * <p>Implementations are constructed once per task by the handler via a
 * selection helper that picks the active backend based on session config
 * (see {@code NomadTaskHandler.selectWorkdirProvider}).</p>
 */
interface DistributedWorkdirProvider {

    /** Short identifier used in log messages and error hints. */
    String name()

    /** Whether this provider is active for the current task (driven by session config). */
    boolean isEnabled()

    /**
     * True if the provider handles stage-in/out via lifecycle tasks (sidecar mode)
     * and {@code .command.run}'s built-in stage scripts must therefore be skipped.
     */
    boolean isExternallyStaged()

    /** Pre-submit setup. Idempotent. After it returns, the {@code get*} accessors are valid. */
    void prepare()

    /** Submission entrypoint (e.g. {@code ['bash', '-c', bootstrapScript]}). */
    List<String> getSubmitCommand()

    /** Extra env vars for the main task. */
    Map<String, String> getSubmitEnv()

    /** Optional Nomad prestart/poststop/sidecar tasks the provider needs. */
    List<NomadLifecycleTaskSpec> getLifecycleTasks()

    /**
     * Build a copy strategy for the BashWrapperBuilder. Return {@code null} to
     * fall back to the default Nomad copy strategy.
     *
     * @param stagingDisabled when true, return a strategy whose stage-in/out
     *                        scripts are no-ops (used when the provider stages
     *                        via lifecycle tasks instead).
     */
    ScriptFileCopyStrategy createCopyStrategy(boolean stagingDisabled)

    /**
     * Wait for the task's remote {@code .exitcode} marker, sync output artifacts
     * back to the operator's workdir, and return the exit code (or {@code null}
     * if the marker never appeared within the configured timeout).
     */
    Integer synchronizeCompletion()

    /** Hint string surfaced in error messages when {@link #synchronizeCompletion()} fails to read the remote exitcode. */
    String getRemoteExitHint()
}
