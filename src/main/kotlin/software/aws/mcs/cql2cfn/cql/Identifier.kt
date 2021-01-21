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
package software.aws.mcs.cql2cfn.cql

import java.util.Locale

data class Identifier(val name: String) {
    constructor(name: String, keepCase: Boolean) : this(name.toInternalName(keepCase))

    override fun toString(): String =
        name

    private companion object {
        fun String.toInternalName(keepCase: Boolean): String =
            if (keepCase) {
                this
            } else {
                toLowerCase(Locale.US)
            }
    }
}

