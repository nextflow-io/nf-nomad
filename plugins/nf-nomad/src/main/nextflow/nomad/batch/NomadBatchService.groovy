/*
 * Copyright 2021, Microsoft Corp
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

package nextflow.nomad.batch

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Global
import nextflow.nomad.config.NomadConfig
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.util.CacheHelper
import nextflow.util.MemoryUnit
import nextflow.util.MustacheTemplateEngine
import nextflow.util.Rnd

/**
 * Implements Nomad Batch operations for Nextflow executor
 *
 * @author mattdsm <ict@cmgg.be>
 */

@Slf4j
@CompileStatic
class NomadBatchService implements Closeable {

    NomadConfig config

    NomadBatchService(NomadBatchExecutor executor) {
        assert executor
        this.config = executor.config
    }

    NomadTaskKey submitTask(TaskRun task) {
        final poolId = getOrCreatePool(task)
        final jobId = getOrCreateJob(poolId, task)
        runTask(poolId, jobId, task)
    }

    NomadTaskKey runTask(String poolId, String jobId, TaskRun task) {
        final taskToAdd = createTask(poolId, jobId, task)
        //apply(() -> client.taskOperations().createTask(jobId, taskToAdd))
        return new NomadTaskKey(jobId, taskToAdd.id())
    }

    void terminate(NomadTaskKey key) {
        //apply(() -> client.taskOperations().terminateTask(key.jobId, key.taskId))
    }

    void deleteTask(NomadTaskKey key) {
        //apply(() -> client.taskOperations().deleteTask(key.jobId, key.taskId))
    }


}