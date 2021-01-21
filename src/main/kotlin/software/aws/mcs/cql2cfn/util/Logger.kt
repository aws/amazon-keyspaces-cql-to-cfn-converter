/*-
 * #%L
 * Amazon Keyspaces CQL Script to CFN Template Converter
 * %%
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package software.aws.mcs.cql2cfn.util

import kotlin.system.exitProcess

object Logger {
    var debugEnabled: Boolean = false
    var exitOnWarn: Boolean = false

    fun debug(message: String) {
        if (debugEnabled) {
            System.err.println("\u001B[0;36m[DEBUG] $message\u001B[0m")
        }
    }

    fun info(message: String) {
        System.err.println("\u001B[0;32m[INFO] $message\u001B[0m")
    }

    fun warn(message: String) {
        System.err.println("\u001B[1;33m[WARN] $message\u001B[0m")
        if (exitOnWarn) {
            exitProcess(1)
        }
    }

    fun error(message: String, e: Throwable? = null): Nothing {
        System.err.println("\u001B[1;91m[ERROR] $message\u001B[0m")
        e?.printStackTrace()
        exitProcess(1)
    }
}
