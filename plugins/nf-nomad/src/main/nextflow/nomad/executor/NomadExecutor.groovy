
package nextflow.nomad.executor

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.executor.Executor
import nextflow.fusion.FusionHelper
import nextflow.nomad.NomadConfig
import nextflow.processor.TaskHandler
import nextflow.processor.TaskMonitor
import nextflow.processor.TaskPollingMonitor
import nextflow.processor.TaskRun
import nextflow.util.Duration
import nextflow.util.ServiceName
import org.pf4j.ExtensionPoint

/**
 * Implement the Nomad executor
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
@Slf4j
@CompileStatic
@ServiceName('nomad')
class NomadExecutor extends Executor implements ExtensionPoint {

    private NomadService service
    private NomadConfig config

    @Override
    protected void register() {
        super.register()
        initNomadService()
    }

    protected void initNomadService(){
        this.config = new NomadConfig((session.config.nomad ?: Collections.emptyMap()) as Map)
        this.service = new NomadService(this.config)
    }

    @Override
    protected TaskMonitor createTaskMonitor() {
        TaskPollingMonitor.create(session, name, 100, Duration.of('5 sec'))
    }

    @Override
    TaskHandler createTaskHandler(TaskRun task) {
        return new NomadTaskHandler(task, this.config, service)
    }

    @Override
    boolean isFusionEnabled() {
        return FusionHelper.isFusionEnabled(session)
    }

    @Override
    boolean isContainerNative() {
        return true
    }
}
