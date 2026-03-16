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
package nextflow.nomad.builders

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Unit tests for JobBuilder priority resolution logic
 *
 * @author Abhinav Sharma <abhi18av@outlook.com>
 */
class JobBuilderPrioritySpec extends Specification {

    @Unroll
    void "should resolve priority string '#value' to #expected"() {
        when:
        Integer result = JobBuilder.resolvePriority(value)

        then:
        result == expected

        where:
        value        || expected
        'critical'   || 100
        'high'       || 80
        'normal'     || 50
        'low'        || 30
        'min'        || 10
        '100'        || 100
        '50'         || 50
        '0'          || 0
        '75'         || 75
        null         || null
        ''           || null
        'invalid'    || null
        '101'        || null  // out of range
        '-1'         || null  // out of range
    }

    void "should handle case-insensitive priority values"() {
        when:
        Integer criticalCase = JobBuilder.resolvePriority('CRITICAL')
        Integer highCase = JobBuilder.resolvePriority('HIGH')
        Integer normalCase = JobBuilder.resolvePriority('Normal')
        Integer lowCase = JobBuilder.resolvePriority('LOW')
        Integer minCase = JobBuilder.resolvePriority('Min')

        then:
        criticalCase == 100
        highCase == 80
        normalCase == 50
        lowCase == 30
        minCase == 10
    }

    void "should validate priority range"() {
        when:
        Integer minValid = JobBuilder.resolvePriority('0')
        Integer maxValid = JobBuilder.resolvePriority('100')
        Integer midValid = JobBuilder.resolvePriority('50')

        then:
        minValid == 0
        maxValid == 100
        midValid == 50
    }

    void "should reject out-of-range numeric values"() {
        when:
        Integer tooHigh = JobBuilder.resolvePriority('101')
        Integer tooLow = JobBuilder.resolvePriority('-1')

        then:
        tooHigh == null
        tooLow == null
    }
}

