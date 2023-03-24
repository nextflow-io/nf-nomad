/*
 * Copyright 2023, University of Stellenbosch, South Africa
 * Copyright 2022, Center for Medical Genetics, Ghent
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

package nextflow.nomad

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import io.nomadproject.client.ApiClient
import java.nio.file.Path
import nextflow.Global
import nextflow.exception.AbortOperationException
import nextflow.executor.Executor
import nextflow.extension.FilesEx
import nextflow.nomad.config.NomadConfig
import nextflow.processor.TaskHandler
import nextflow.processor.TaskMonitor
import nextflow.processor.TaskPollingMonitor
import nextflow.processor.TaskRun
import nextflow.util.Duration
import nextflow.util.ServiceName
import org.pf4j.ExtensionPoint

/**
 * Nextflow executor for Nomad batch
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 * @author matthdsm <ict@cmgg.be>
 */

@Slf4j
@ServiceName('nomad')
@CompileStatic
class NomadExecutor extends Executor implements ExtensionPoint {

    private Path remoteBinDir

    private NomadConfig config

    private NomadClient nomadClient

    /**
     * @return {@code true} to signal containers are managed directly the Nomad executor
     */
    final boolean isContainerNative() {
        return true
    }

    @Override
    String containerConfigEngine() {
        return 'docker'
    }

    @Override
    Path getWorkDir() {
        session.workDir
    }

    protected void validateWorkDir() {
        /*
         * Make sure the work dir is a local path
         */
        if (!(workDir instanceof Path)) {
            session.abort()
            throw new AbortOperationException("When using `$name` executor a local path must be provided as working directory")
        }
    }

    protected void validatePathDir() {
        def path = session.config.navigate('env.PATH')
        if (path) {
            log.warn "Environment PATH defined in config file is ignored by Nomad executor"
        }
    }

    protected void uploadBinDir() {
        /*
         * upload local binaries
         */
        if (session.binDir && !session.binDir.empty() && !session.disableRemoteBinDir) {
            final remote = getTempDir()
            log.info "Uploading local `bin` scripts folder to ${remote.toUriString()}/bin"
            remoteBinDir = FilesEx.copyTo(session.binDir, remote)
        }
    }

    protected void initBatchService() {
        config = NomadConfig.getConfig(session)
        batchService = new NomadService(this)

        nomadBatchExecutor = new ApiClient(this)

        Global.onCleanup((it) -> batchService.close())
    }

    /**
     * Initialise the Nomad executor.
     */
    @Override
    protected void register() {
        super.register()
        initBatchService()
        validateWorkDir()
        validatePathDir()
        uploadBinDir()
    }

    @PackageScope NomadConfig getConfig() {
        return config
    }

    @Override
    protected TaskMonitor createTaskMonitor() {
        TaskPollingMonitor.create(session, name, 1000, Duration.of('10 sec'))
    }

    @Override
    TaskHandler createTaskHandler(TaskRun task) {
        return new NomadTaskHandler(task, this)
    }

    NomadService getBatchService() {
        return batchService
    }

    NomadExecutor getNomadBatchExecutor() {
        return nomadBatchExecutor
    }

    Path getRemoteBinDir() { return remoteBinDir }


}
