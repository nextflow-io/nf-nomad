/*
 * Copyright 2023-, Stellenbosch University, South Africa
 * Copyright 2024, Evaluacion y Desarrollo de Negocios, Spain
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

package nextflow.nomad

/**
 * Nomad API Helper methods
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */

class NomadHelper {

    static String sanitizeName(String name) {
        def str = "nf-${name}"
        str = str.replaceAll(/[^a-zA-Z0-9\.\_\-]+/, '_')
        str = str.replaceAll(/^[^a-zA-Z0-9]+/, '')
        str = str.replaceAll(/[^a-zA-Z0-9]+$/, '')
        return str.take(60)
    }

    /**
     * Build a child Nomad job name in the structured form
     * {@code nf-<short-session>-<short-task>-<process>}.
     * <ul>
     *   <li>{@code short-session} = first 8 chars of the Nextflow Session uniqueId</li>
     *   <li>{@code short-task}    = first 8 chars of the task hash</li>
     *   <li>{@code process}       = sanitized process name. Long names are
     *       preserved verbatim (Nomad accepts long job IDs — observed in
     *       practice with deeply-nested nf-core subworkflow paths like
     *       {@code NFCORE_FETCHNGS:SRA:FASTQ_DOWNLOAD_PREFETCH_FASTERQDUMP_SRATOOLS:CUSTOM_SRATOOLSNCBISETTINGS}).
     *       Only when the full job-id would exceed {@value #JOB_ID_MAX_LEN}
     *       chars do we truncate the process tail and append a 7-char SHA1
     *       suffix for collision resistance.</li>
     * </ul>
     *
     * Distinct between concurrent sessions (different prefixes) — solves the
     * name-clash bug from the legacy {@code nf-<task-hash>-<process>} scheme
     * where two parallel runs of the same pipeline collided on the same Nomad
     * job ID. Stable across retries within a session (cache-friendly).
     *
     */
    static final int JOB_ID_MAX_LEN = 128

    static String childJobName(String sessionId, String taskHash, String processName) {
        // The child-job prefix is `<runtag>-<8task>-`. The runtag is supplied
        // by the harness via NF_NOMAD_RUN_TAG (e.g. `abhi-admin-8791`) and
        // pairs with a head job-id of `<runtag>-nf-head-<pipeline-slug>`,
        // so a single `nomad job status -prefix <runtag>-` lists head +
        // every worker for the run.
        //
        // The pipeline slug is not repeated on children — the process name
        // already encodes pipeline context (e.g. `NFCORE_DEMO_DEMO_FASTQC`).
        //
        // Fallback: when NF_NOMAD_RUN_TAG is unset, derive the prefix from
        // the Nextflow session UUID and prepend `nf-` as a cluster marker
        // (`nf-<8sess>-<8task>-<process>`). This preserves vanilla
        // standalone behaviour for users not running under abc-cluster-cli.
        //
        // The env value is sanitized: alphanumeric / dot / underscore /
        // dash kept; everything else collapsed to dash.
        String runTag = sanitizeEnvFragment(System.getenv('NF_NOMAD_RUN_TAG'))
        String shortTask = take8(taskHash)
        String proc = sanitizeProcess(processName ?: 'task')
        String prefix = runTag
                ? "${runTag}-${shortTask}-"
                : "nf-${take8(sessionId)}-${shortTask}-"
        int budget = JOB_ID_MAX_LEN - prefix.length()
        if( proc.length() <= budget )
            return prefix + proc
        // Too long: truncate process to (budget - 8) chars + '-' + 7-char SHA1 suffix.
        int headLen = Math.max(1, budget - 8)
        String head = proc.substring(0, headLen)
        java.security.MessageDigest md = java.security.MessageDigest.getInstance('SHA-1')
        byte[] digest = md.digest((processName ?: 'task').getBytes('UTF-8'))
        StringBuilder hex = new StringBuilder()
        for( byte b : digest ) hex.append(String.format('%02x', b))
        return prefix + head + '-' + hex.toString().substring(0, 7)
    }

    /**
     * Sanitize a harness-supplied env-var fragment (NF_NOMAD_PIPELINE_SLUG /
     * NF_NOMAD_RUN_TAG) for use inside a Nomad job-id. Keeps alphanumeric
     * + dot/underscore/dash; collapses everything else to a single dash;
     * trims leading/trailing dashes. Returns "" when the input is null,
     * empty, or sanitizes away to nothing.
     */
    private static String sanitizeEnvFragment(String s) {
        if( !s ) return ''
        String out = s.replaceAll(/[^A-Za-z0-9._-]+/, '-').replaceAll(/^-+|-+$/, '')
        return out ?: ''
    }

    /** Take first 8 chars (or fewer if shorter) of a string, dashes/non-alnum stripped. */
    private static String take8(String s) {
        if( !s ) return '00000000'
        String clean = s.replaceAll(/[^a-zA-Z0-9]/, '')
        return clean.length() >= 8 ? clean.substring(0, 8) : clean.padRight(8, '0')
    }

    /**
     * Sanitize a Nextflow process name for Nomad job-id use. Process names
     * can include {@code :}, spaces, parentheses (e.g. {@code RNASEQ:FASTQC (sample_1)}).
     * Replaces non-alphanumeric with underscore. Length is preserved here;
     * {@link #childJobName} handles the overall job-id budget.
     */
    private static String sanitizeProcess(String name) {
        return name.replaceAll(/[^a-zA-Z0-9\.\_]+/, '_')
                   .replaceAll(/^_+/, '')
                   .replaceAll(/_+$/, '')
    }

}
