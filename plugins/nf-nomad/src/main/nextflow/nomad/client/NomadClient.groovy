package nextflow.nomad.client

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.nomad.config.NomadClientOpts
import nextflow.nomad.config.NomadConfig

/**
 * Nomad API client
 *
 * Tip: Use the following command to find out your kubernetes master node
 *    nomad node status
 *
 * See
 *   https://developer.hashicorp.com/nomad/api-docs/jobs
 *
 * Useful cheatsheet
 *   https://developer.hashicorp.com/nomad/docs/commands
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */


@Slf4j
@CompileStatic
class NomadClient {

    protected NomadClientOpts clientOpts

    private HttpURLConnection conn

    NomadClient(NomadConfig config) {
        log.debug "[Nomad] Creating Nomad client using the configuration options ${config.client()}."
        this.clientOpts = config.client()
    }

    protected HttpURLConnection createConnection0(String url) {
        new URL(url).openConnection() as HttpURLConnection
    }

    HttpURLConnection closeConnection() {
        this.conn.disconnect()
    }

    /**
     * Makes a HTTP(S) request the Nomad server
     *
     * @param method The HTTP verb to use eg. {@code GET}, {@code POST}, etc
     * @param path The API action path
     * @param body The request payload
     * @return
     *      A two elements list in which the first entry is an integer representing the HTTP response code,
     *      the second element is the text (json) response
     */
    protected NomadResponseApi makeRequest(String method, String path, String body=null) throws NomadResponseException {


        //FIXME Do not hardcode
        final int maxRetries = 5
        int attempt = 0

        while ( true ) {
            try {
                return makeRequestCall( method, path, body )
            } catch ( NomadResponseException | SocketException | SocketTimeoutException e ) {
                if ( e instanceof NomadResponseException && e.response.code != 500 )
                    throw e
                if ( ++attempt > maxRetries )
                    throw e
                log.debug "[Nomad] API request threw socket exception: $e.message for $method $path - Retrying request (attempt=$attempt)"
                final long delay = (Math.pow(3, attempt - 1) as long) * 250
                sleep( delay )
            }
        }
    }

    static private void debug(String method, String path, String text=null) {
        log.debug "[Nomad] API request $method $path ${text ? '\n'+prettyPrint(text).indent() : ''}"
    }

    private NomadResponseApi makeRequestCall(String method, String path, String body=null) throws NomadResponseException {
        final endpoint = clientOpts.server + path

        debug(method, endpoint, body)
        conn = createConnection0(endpoint)

        conn.setRequestProperty("Content-Type", "application/json")
        if( clientOpts.token ) {
            conn.setRequestProperty("Authorization", "Bearer ${clientOpts.token}")
        }

        if( !method ) method = body ? 'POST' : 'GET'
        conn.setRequestMethod(method)

        if( body ) {
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.getOutputStream() << body
            conn.getOutputStream().flush()
        }

        final code = conn.getResponseCode()
        final isError = code >= 400
        final stream = isError ? conn.getErrorStream() : conn.getInputStream()
        if( isError )
            throw new NomadResponseException("Request $method $path returned an error code=$code", stream)
        return new NomadResponseApi(code, stream)
    }


    NomadResponseApi get(String path) {
        def method="GET"
        makeRequest(method,path)
    }

    NomadResponseApi post(String path, String body) {
        def method="POST"
        makeRequest(method,path, body)
    }

    NomadResponseApi put(String path, String body) {
        def method="PUT"
        makeRequest(method,path, body)
    }

    NomadResponseApi delete(String path, String body=null) {
        def method="DELETE"
        makeRequest(method,path, body)
    }

    static protected String prettyPrint(String json) {
        try {
            JsonOutput.prettyPrint(json)
        }
        catch( Exception e ) {
            return json
        }
    }

}
