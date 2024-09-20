package nextflow.nomad.config

import groovy.transform.CompileStatic
import nextflow.util.Duration


@CompileStatic
class RetryConfig {

    Duration delay = Duration.of('250ms')
    Duration maxDelay = Duration.of('90s')
    int maxAttempts = 10
    double jitter = 0.25

    RetryConfig(){
        this(Collections.emptyMap())
    }

    RetryConfig(Map config){
        if( config.delay )
            delay = config.delay as Duration
        if( config.maxDelay )
            maxDelay = config.maxDelay as Duration
        if( config.maxAttempts )
            maxAttempts = config.maxAttempts as int
        if( config.jitter )
            jitter = config.jitter as double
    }
}
