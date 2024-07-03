/*
 * Copyright 2024-2024, Evaluacion y Desarrollo de Negocios, Spain
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


import groovyx.gpars.dataflow.DataflowBroadcast
import nextflow.Session
import nextflow.executor.Executor
import nextflow.executor.ExecutorFactory
import nextflow.nomad.executor.NomadExecutor
import nextflow.processor.TaskHandler
import nextflow.processor.TaskMonitor
import nextflow.script.BaseScript
import nextflow.script.ChannelOut
import nextflow.script.ScriptRunner
import nextflow.script.ScriptType

/**
 * Mock runner for test
 *
 * @author Jorge Aguilera <jagedn@gmail.com>
 */

class MockScriptRunner extends ScriptRunner {

    MockScriptRunner() {
        super(new MockSession())
    }

    MockScriptRunner(Map config) {
        super(new MockSession(config))
    }

    MockScriptRunner setScript(String str) {
        def script = TestHelper.createInMemTempFile('main.nf', str)
        setScript(script)
        return this
    }

    MockScriptRunner invoke() {
        execute()
        return this
    }

    BaseScript getScript() { getScriptObj() }

    @Override
    def normalizeOutput(output) {
        if( output instanceof ChannelOut ) {
            def list = new ArrayList(output.size())
            for( int i=0; i<output.size(); i++ ) {
                list.add(read0(output[i]))
            }
            return list.size() == 1 ? list[0] : list
        }

        if( output instanceof Object[] || output instanceof List) {
            def result = new ArrayList<>(output.size())
            for( def item : output ) {
                ((List)result).add(read0(item))
            }
            return result
        }

        else {
            return read0(output)
        }
    }


    private read0( obj ) {
        if( obj instanceof DataflowBroadcast )
            return obj.createReadChannel()
        return obj
    }

}

class MockSession extends Session {

    @Override
    Session start() {
        this.executorFactory = new MockExecutorFactory()
        return super.start()
    }

    MockSession() {
        super()
    }

    MockSession(Map config) {
        super(config)
    }
}

class MockExecutorFactory extends ExecutorFactory {
    @Override
    protected Class<? extends Executor> getExecutorClass(String executorName) {
        return MockExecutor
    }

    @Override
    protected boolean isTypeSupported(ScriptType type, Object executor) {
        true
    }
}


class MockExecutor extends NomadExecutor {

    @Override
    void signal() { }

}

class MockMonitor implements TaskMonitor {

    void schedule(TaskHandler handler) {
        handler.submit()
    }

    /**
     * Remove the {@code TaskHandler} instance from the queue of tasks to be processed
     *
     * @param handler A not null {@code TaskHandler} instance
     */
    boolean evict(TaskHandler handler) { }

    /**
     * Start the monitoring activity for the queued tasks
     * @return The instance itself, useful to chain methods invocation
     */
    TaskMonitor start() { }

    /**
     * Notify when a task terminates
     */
    void signal() { }
}
