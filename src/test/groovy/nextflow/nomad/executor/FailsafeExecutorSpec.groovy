package nextflow.nomad.executor

import io.nomadproject.client.ApiException
import dev.failsafe.FailsafeException
import nextflow.nomad.config.RetryConfig
import nextflow.util.Duration
import spock.lang.Specification

import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class FailsafeExecutorSpec extends Specification {

    private static RetryConfig fastRetryConfig(int maxAttempts = 3) {
        new RetryConfig([
                delay      : Duration.of('1ms'),
                maxDelay   : Duration.of('2ms'),
                maxAttempts: maxAttempts,
                jitter     : 0d
        ])
    }

    void "should retry transient api exceptions and eventually succeed"() {
        given:
        def executor = new FailsafeExecutor(fastRetryConfig(3))
        def attempts = new AtomicInteger(0)

        when:
        def result = executor.apply {
            int attempt = attempts.incrementAndGet()
            if( attempt < 3 ) {
                throw new ApiException(429, "too many requests")
            }
            return "ok"
        }

        then:
        result == "ok"
        attempts.get() == 3
    }

    void "should not retry non transient api exceptions"() {
        given:
        def executor = new FailsafeExecutor(fastRetryConfig(5))
        def attempts = new AtomicInteger(0)

        when:
        executor.apply {
            attempts.incrementAndGet()
            throw new ApiException(400, "bad request")
        }

        then:
        def e = thrown(FailsafeException)
        e.cause instanceof ApiException
        (e.cause as ApiException).code == 400
        attempts.get() == 1
    }

    void "should retry io and timeout failures wrapped in runtime exception"() {
        given:
        def executor = new FailsafeExecutor(fastRetryConfig(4))
        def attempts = new AtomicInteger(0)

        when:
        def result = executor.apply {
            int attempt = attempts.incrementAndGet()
            if( attempt == 1 ) {
                throw new RuntimeException(new IOException("network glitch"))
            }
            if( attempt == 2 ) {
                throw new RuntimeException(new TimeoutException("timeout"))
            }
            return "recovered"
        }

        then:
        result == "recovered"
        attempts.get() == 3
    }
}
