/*
 * Copyright 2023-, Stellenbosch University, South Africa
 * Copyright 2024, Evaluacion y Desarrollo de Negocios, Spain
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
package nextflow.nomad.util

import groovy.transform.CompileStatic
import org.slf4j.Logger

@CompileStatic
class NomadLogging {
    private static final String NOMAD_DEBUG_ENV = 'NF_NOMAD_DEBUG'

    static int debugLevel() {
        resolveDebugLevel(System.getenv(NOMAD_DEBUG_ENV))
    }

    static int resolveDebugLevel(String value) {
        String debug = value?.trim()?.toLowerCase()
        if( !debug )
            return 0

        try {
            return Integer.parseInt(debug)
        }
        catch( NumberFormatException ignored ) {
            if( debug == 'debug' )
                return 1
            if( debug == 'trace' )
                return 2
            return 0
        }
    }

    static boolean isDebugEnabled() {
        debugLevel() >= 1
    }

    static boolean isTraceEnabled() {
        debugLevel() >= 2
    }

    static void logJobState(Logger logger, String jobId, String state, Map details = [:]) {
        if (!isDebugEnabled()) return
        String msg = "[NOMAD] Job state: jobId=$jobId, state=$state"
        if (details) {
            msg += ", " + details.collect { k, v -> "$k=$v" }.join(", ")
        }
        logger.info(msg)
    }

    static void logTiming(Logger logger, String operation, long durationMs) {
        if (!isDebugEnabled()) return
        logger.info("[NOMAD] $operation completed in ${durationMs}ms")
    }

    static void logAllocationDetails(Logger logger, String jobId, Map allocation) {
        if (!isTraceEnabled()) return
        String msg = """[NOMAD-TRACE] Allocation details for $jobId:
          - NodeID: ${allocation.nodeName}
          - ClientStatus: ${allocation.clientStatus}
          - DesiredStatus: ${allocation.desiredStatus}
          - ModifyIndex: ${allocation.modifyIndex}""".stripIndent()
        logger.info(msg)
    }

    static void logApiCall(Logger logger, String method, String endpoint,
                           String requestBody = null, String responseBody = null) {
        if (!isTraceEnabled()) return
        logger.info("[NOMAD-API] $method $endpoint")
        if (requestBody) {
            logger.info("[NOMAD-API-REQ] $requestBody")
        }
        if (responseBody) {
            logger.info("[NOMAD-API-RES] $responseBody")
        }
    }

    static void logConfiguration(Logger logger, String category, Map config) {
        if (!isDebugEnabled()) return
        String configStr = config.collect { k, v ->
            (v instanceof String && v.contains('secret')) ||
                    (v instanceof String && v.contains('token')) ||
                    (v instanceof String && v.contains('key')) ? "$k=***" : "$k=$v"
        }.join(", ")
        logger.info("[NOMAD-CONFIG] $category: $configStr")
    }

    static void logResources(Logger logger, String jobId, Map resources) {
        if (!isDebugEnabled()) return
        logger.info("[NOMAD] Job $jobId resources: " +
                "cpus=${resources.cpus}, memory=${resources.memory}MB, " +
                "disk=${resources.disk}MB")
    }

    static void logRetry(Logger logger, String operation, int attempt,
                         long delayMs, String reason = null) {
        if (!isDebugEnabled()) return
        String msg = "[NOMAD] Retry $operation (attempt $attempt, waiting ${delayMs}ms)"
        if (reason) msg += ": $reason"
        logger.info(msg)
    }

    static void logError(Logger logger, String operation, String jobId = null,
                         Throwable error = null, Map context = [:]) {
        String msg = "[NOMAD] Error during $operation"
        if (jobId) msg += " for job $jobId"
        if (context) msg += ": " + context.collect { k, v -> "$k=$v" }.join(", ")

        logger.warn(msg)
        if (isTraceEnabled() && error) {
            logger.info("[NOMAD-TRACE] Exception:", error)
        }
    }

    static void logTaskSubmission(Logger logger, String taskName, String jobId,
                                  String container, String workDir) {
        if (!isDebugEnabled()) return
        logger.info("[NOMAD] Task submission: task=$taskName, jobId=$jobId, " +
                "container=$container, workDir=$workDir")
    }

    static void logStateTransition(Logger logger, String jobId, String oldState, String newState) {
        if (!isDebugEnabled()) return
        logger.info("[NOMAD] Job state transition: jobId=$jobId, oldState=$oldState -> newState=$newState")
    }
}
