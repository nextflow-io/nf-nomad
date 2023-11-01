
package nextflow.nomad.executor

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import nextflow.Global
import nextflow.exception.AbortOperationException
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
 * Implement the Nomad executor
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Slf4j
@CompileStatic
@ServiceName('nomad')
class NomadExecutor extends Executor implements  ExtensionPoint {

    private Path remoteBinDir

    private NomadConfig config

    private NomadService service

    /**
     * @return {@code true} to signal containers are managed directly by the Nomad cluster
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
         * make sure the work dir is an Azure bucket
         */
        if( !(workDir instanceof Path) ) {
            session.abort()
            throw new AbortOperationException("When using `$name` executor a working directory must be provided using the `-work-dir` command line option")
        }
    }
    protected void validatePathDir() {
        def path = session.config.navigate('env.PATH')
        if( path ) {
            log.warn "Environment PATH defined in config file is ignored by Nomad executor"
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

    protected void initNomadService() {
        this.config = NomadConfig.getConfig(session)
        this.service = new NomadService(this)

        Global.onCleanup((it) -> service.client.closeConnection())
    }

    /**
     * Initialise the Nomad executor.
     */
    @Override
    protected void register() {
        super.register()
        initNomadService()
//        validateWorkDir()
//        validatePathDir()
//        uploadBinDir()
    }

    @PackageScope NomadConfig getConfig() {
        return config
    }

    @Override
    protected TaskMonitor createTaskMonitor() {
        TaskPollingMonitor.create(session, name, 100, Duration.of('5 sec'))
    }

    @Override
    TaskHandler createTaskHandler(TaskRun task) {
        return new NomadTaskHandler(task, this)
    }

    NomadService getService() {
        return service
    }

    Path getRemoteBinDir() { return remoteBinDir }

    @Override
    boolean isFusionEnabled() {
        return FusionHelper.isFusionEnabled(session)
    }

}
