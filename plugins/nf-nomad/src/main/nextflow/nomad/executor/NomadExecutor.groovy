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

package nextflow.nomad.executor

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import nextflow.Global
import nextflow.executor.Executor
import nextflow.extension.FilesEx
import nextflow.fusion.FusionHelper
import nextflow.nomad.config.NomadConfig
import nextflow.processor.TaskHandler
import nextflow.processor.TaskMonitor
import nextflow.processor.TaskPollingMonitor
import nextflow.processor.TaskRun
import nextflow.util.Duration
import nextflow.util.ServiceName
import org.pf4j.ExtensionPoint

import java.nio.file.Path

/**
 * Implement the Kubernetes executor
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@ServiceName('nomad')
class NomadExecutor extends Executor implements ExtensionPoint {

    private Path remoteBinDir

    private NomadConfig config

    private NomadService service

    /**
     * @return {@code true} to signal containers are managed directly the AWS Batch service
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


    protected void validatePathDir() {
        def path = session.config.navigate('env.PATH')
        if( path ) {
            log.warn "Environment PATH defined in config file is ignored by Nomadure Batch executor"
        }
    }

    protected void uploadBinDir() {
        /*
         * upload local binaries
         */
        if( session.binDir && !session.binDir.empty() && !session.disableRemoteBinDir ) {
            final remote = getTempDir()
            log.info "Uploading local `bin` scripts folder to ${remote.toUriString()}/bin"
            remoteBinDir = FilesEx.copyTo(session.binDir, remote)
        }
    }

    protected void initService() {
        config = NomadConfig.getConfig(session)
        service = new NomadService(this)

        Global.onCleanup((it) -> service.close())
    }

    /**
     * Initialise the Nomad executor.
     */
    @Override
    protected void register() {
        super.register()
        initService()
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
//        def handler = new NomadTaskHandler(task, this)
//        return handler
        return null
    }

    NomadService getService() {
        return service
    }


    @Override
    boolean isFusionEnabled() {
        return FusionHelper.isFusionEnabled(session)
    }

}
