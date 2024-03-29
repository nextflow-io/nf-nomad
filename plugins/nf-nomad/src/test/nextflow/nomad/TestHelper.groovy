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

package nextflow.nomad

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs

import java.nio.file.Files
import java.nio.file.Path

/**
 * Helper class for tests
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

class TestHelper {
    static private fs = Jimfs.newFileSystem(Configuration.unix());

    static Path createInMemTempFile(String name='temp.file', String content=null) {
        Path tmp = fs.getPath("/tmp");
        tmp.mkdir()
        def result = Files.createTempDirectory(tmp, 'test').resolve(name)
        if( content )
            result.text = content
        return result
    }
}
