package nextflow.nomad.executor

import dev.failsafe.Failsafe
import dev.failsafe.RetryPolicy
import dev.failsafe.RetryPolicyBuilder
import dev.failsafe.event.EventListener
import dev.failsafe.event.ExecutionAttemptedEvent
import dev.failsafe.function.CheckedSupplier
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.nomadproject.client.ApiException
import nextflow.nomad.config.RetryConfig

import java.lang.reflect.Proxy
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException
import java.util.function.Predicate

@Slf4j
@CompileStatic
class FailsafeExecutor {

    private RetryConfig config

    FailsafeExecutor(RetryConfig config){
        this.config = config
    }


    protected <T> RetryPolicyBuilder<T> builderWithHandleIf(Predicate<? extends Throwable> cond){
        def builder = RetryPolicy.<T>builder()

        // 3.1.0
        def predicateMethod = builder.class.methods.find {
            it.name == 'handleIf' && it.parameterCount == 1 && it.parameterTypes[0] == Predicate}
        if (predicateMethod) {
            return builder.handleIf(cond)
        }

        // 3.3.2
        def checkedpredicateMethod = builder.class.methods.find {
            it.name == 'handleIf' && it.parameterCount == 1 && it.parameterTypes[0].name.endsWith("CheckedPredicate")}
        if (!checkedpredicateMethod) {
            throw new IllegalStateException("No valid failsafe library detected.")
        }

        def targetParamType = checkedpredicateMethod.parameterTypes[0]
        def proxyInstance = Proxy.newProxyInstance(
                targetParamType.classLoader,
                [targetParamType] as Class[],
                { proxy, m, args ->
                    return cond.test((Throwable)args[0])
                }
        )
        checkedpredicateMethod.invoke(builder, proxyInstance)

        return builder
    }

    protected <T> RetryPolicy<T> retryPolicy(Predicate<? extends Throwable> cond) {

        final listener = new EventListener<ExecutionAttemptedEvent<T>>() {
            @Override
            void accept(ExecutionAttemptedEvent<T> event) throws Throwable {
                log.debug("Nomad TooManyRequests response error - attempt: ${event.attemptCount}; reason: ${event.lastFailure.message}")
            }
        }
        RetryPolicyBuilder<T> builder = builderWithHandleIf(cond)
        return builder
                .withBackoff(config.delay.toMillis(), config.maxDelay.toMillis(), ChronoUnit.MILLIS)
                .withMaxAttempts(config.maxAttempts)
                .withJitter(config.jitter)
                .onRetry(listener)
                .build()
    }

    /*
    408 Request Timeout
    429 Too Many Requests
    500 Internal Server Error
    502 Bad Gateway
    503 Service Unavailable
    504 Gateway Timeout
     */
    final private static List<Integer> RETRY_CODES = List.of(408, 429, 500, 502, 503, 504)

    protected <T> T apply(CheckedSupplier<T> action) {
        // define the retry condition
        final cond = new Predicate<? extends Throwable>() {
            @Override
            boolean test(Throwable t) {
                if( t instanceof ApiException && t.code in RETRY_CODES )
                    return true
                if( t instanceof IOException || t.cause instanceof IOException )
                    return true
                if( t instanceof TimeoutException || t.cause instanceof TimeoutException )
                    return true
                return false
            }
        }
        // create the retry policy object
        final policy = retryPolicy(cond)
        // apply the action with
        return Failsafe.with(policy).get(action)
    }

}
