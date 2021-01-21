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
package software.aws.mcs.cql2cfn.cfn

import software.amazon.awscdk.core.App
import software.amazon.awscdk.core.CfnResource
import software.amazon.awscdk.core.Stack
import software.aws.mcs.cql2cfn.cql.Identifier
import software.aws.mcs.cql2cfn.cql.Statement
import java.io.File

class CfnTemplateGenerator {
    private val app: App = App()

    val stack: Stack = Stack(app)
    var usedKeyspace: Identifier? = null
    val createdKeyspaces: MutableMap<Identifier, CfnResource> = mutableMapOf()
    val createdTypes: MutableMap<Pair<Identifier, Identifier>, CfnResource> = mutableMapOf()
    val createdTables: MutableMap<Pair<Identifier, Identifier>, CfnResource> = mutableMapOf()

    fun handle(statement: Statement): Unit =
        when (statement) {
            is Statement.Use ->
                UseHandler.handle(this, statement)
            is Statement.CreateKeyspace ->
                CreateKeyspaceHandler.handle(this, statement)
            is Statement.CreateTable ->
                CreateTableHandler.handle(this, statement)
        }

    fun generate(): String =
        File(app.synth().stacks.first().templateFullPath).readText()
}
