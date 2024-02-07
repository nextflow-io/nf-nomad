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

package nextflow.nomad.client

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
/**
 * Model a kubernetes invalid response
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

@Slf4j
@CompileStatic
class NomadResponseException extends Exception {

    NomadResponseJson response

    NomadResponseException(NomadResponseJson response) {
        super(msg0(response))
        this.response = response
    }

    NomadResponseException(String message, NomadResponseJson response) {
        super(msg1(message,response))
        this.response = response
    }

    NomadResponseException(String message, InputStream response) {
        this(message, new NomadResponseJson(fetch(response)))
    }

    static private String msg1(String msg, NomadResponseJson resp ) {
        if( !msg && resp==null )
            return null

        if( msg && resp != null ) {
            def sep = resp.isRawText() ? ' -- ' : '\n'
            return "${msg}${sep}${msg0(resp)}"
        }
        else if( msg ) {
            return msg
        }
        else {
            return msg0(resp)
        }
    }

    static private String msg0(NomadResponseJson response ) {
        if( response == null )
            return null

        if( response.isRawText() )
            response.getRawText()
        else
            "\n${response.toString().indent('  ')}"
    }

    static private String fetch(InputStream stream) {
        try {
            return stream?.text
        }
        catch( Exception e ) {
            log.debug "Unable to fetch response text -- Cause: ${e.message ?: e}"
            return null
        }
    }

}
