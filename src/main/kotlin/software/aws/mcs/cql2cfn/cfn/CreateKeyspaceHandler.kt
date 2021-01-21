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

import software.amazon.awscdk.core.Tags
import software.amazon.awscdk.services.cassandra.CfnKeyspace
import software.aws.mcs.cql2cfn.cql.Identifier
import software.aws.mcs.cql2cfn.cql.PropertyMap
import software.aws.mcs.cql2cfn.cql.Statement
import software.aws.mcs.cql2cfn.util.Logger

object CreateKeyspaceHandler : StatementHandler<Statement.CreateKeyspace>() {
    override fun CfnTemplateGenerator.doHandle(statement: Statement.CreateKeyspace) {
        Logger.info("Processing CREATE KEYSPACE statement for '${statement.ksName}'")
        if (shouldSkipKeyspace(statement.ksName, statement.ifNotExists)) {
            return
        }
        val keyspaceBuilder = CfnKeyspace.Builder
            .create(stack, "Keyspace${createdKeyspaces.size + 1}")
            .keyspaceName(statement.ksName.name)
        val keyspace = keyspaceBuilder.build()
        processRegularProperties(statement.ksProps.regularProperties, keyspace)
        processCustomProperties(statement.ksProps.customProperties, keyspace)
        createdKeyspaces[statement.ksName] = keyspace
    }

    private fun CfnTemplateGenerator.shouldSkipKeyspace(
        ksName: Identifier,
        ifNotExists: Boolean
    ): Boolean =
        if (createdKeyspaces.containsKey(ksName)) {
            if (ifNotExists) {
                Logger.warn("Attempted to create keyspace again with IF NOT EXISTS specified")
                true
            } else {
                Logger.error("Cannot create more than one keyspace with the same name " +
                        "unless IF NOT EXISTS is specified")
            }
        } else {
            false
        }

    private fun CfnTemplateGenerator.processRegularProperties(
        regularProperties: PropertyMap<Identifier, Any>,
        keyspace: CfnKeyspace
    ) {
        if (!regularProperties.containsKey(Identifier("replication"))) {
            Logger.error("Missing required property 'replication'")
        }
        regularProperties.forEach { (key, value) ->
            when (key.name) {
                "replication" -> {
                    @Suppress("UNCHECKED_CAST")
                    val replication = (value as? PropertyMap<String, String>)?.toMutableMap()
                        ?: Logger.error("Invalid value for property 'replication'; it should be a map")
                    when (val clazz = replication.remove("class")) {
                        null ->
                            Logger.error("Missing required option 'class' of property 'replication'")
                        "SingleRegionStrategy" ->
                            if (replication.isNotEmpty()) {
                                Logger.error("Unrecognized option '${replication.keys.first()}' " +
                                        "for replication class 'SingleRegionStrategy'")
                            }
                        else ->
                            Logger.warn("Replication class '${clazz}' is not applicable to Keyspaces")
                    }
                }
                "tags" -> {
                    @Suppress("UNCHECKED_CAST")
                    val tags = value as? PropertyMap<String, String>
                        ?: Logger.error("Invalid value for property 'tags'; it should be a map")
                    Tags.of(keyspace).apply {
                        tags.forEach { add(it.key, it.value) }
                    }
                    Logger.error("Property 'tags' currently cannot be set via CloudFormation")
                }
                else ->
                    Logger.warn("Property '$key' is not applicable to Keyspaces")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun CfnTemplateGenerator.processCustomProperties(
        customProperties: PropertyMap<String, PropertyMap<String, String>>?,
        keyspace: CfnKeyspace
    ) {
        if (customProperties != null) {
            Logger.error("Custom properties are not supported for keyspace yet")
        }
    }
}
