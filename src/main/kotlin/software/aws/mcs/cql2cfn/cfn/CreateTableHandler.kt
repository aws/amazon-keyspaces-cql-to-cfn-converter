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

import software.amazon.awscdk.core.Fn
import software.amazon.awscdk.core.Tags
import software.amazon.awscdk.services.cassandra.CfnTable
import software.aws.mcs.cql2cfn.cql.Identifier
import software.aws.mcs.cql2cfn.cql.PropertyMap
import software.aws.mcs.cql2cfn.cql.Statement
import software.aws.mcs.cql2cfn.cql.TableProperties
import software.aws.mcs.cql2cfn.cql.Type
import software.aws.mcs.cql2cfn.util.Logger

object CreateTableHandler : StatementHandler<Statement.CreateTable>() {
    override fun CfnTemplateGenerator.doHandle(statement: Statement.CreateTable) {
        Logger.info("Processing CREATE TABLE statement " +
                "for '${statement.ksName?.let { "$it." } ?: ""}${statement.cfName}'")
        val ksName = resolveKeyspace(statement.ksName)
        if (shouldSkipTable(ksName, statement.cfName, statement.ifNotExists)) {
            return
        }
        val tableBuilder = CfnTable.Builder
            .create(stack, "Table${createdTables.size + 1}")
            .keyspaceName(getKeyspaceNameOrRef(ksName))
            .tableName(statement.cfName.name)
        processColumns(
            ksName,
            statement.cfProps.columnDefinitions,
            statement.cfProps.staticColumnNames,
            statement.cfProps.partitionKeyColumnNames,
            statement.cfProps.clusteringKeyColumnNames,
            statement.cfProps.clusteringOrders,
            tableBuilder
        )
        val table = tableBuilder.build()
        processRegularProperties(statement.cfProps.regularProperties, table)
        processCustomProperties(statement.cfProps.customProperties, table)
        processCompactStorage(statement.cfProps.compactStorage)
        createdTables[Pair(ksName, statement.cfName)] = table
    }

    private fun CfnTemplateGenerator.resolveKeyspace(ksName: Identifier?): Identifier =
        (ksName ?: usedKeyspace) ?: Logger.error("No keyspace specified for table")

    private fun CfnTemplateGenerator.getKeyspaceNameOrRef(ksName: Identifier): String =
        createdKeyspaces[ksName]?.ref
            ?: ksName.name.also {
                Logger.warn("Attempted to create table in keyspace before creating it first")
            }

    private fun CfnTemplateGenerator.shouldSkipTable(
        ksName: Identifier,
        cfName: Identifier,
        ifNotExists: Boolean
    ): Boolean =
        if (createdTables.containsKey(Pair(ksName, cfName))) {
            if (ifNotExists) {
                Logger.warn("Attempted to create table again with IF NOT EXISTS specified")
                true
            } else {
                Logger.error("Cannot create more than one table with the same name " +
                        "unless IF NOT EXISTS is specified")
            }
        } else {
            false
        }

    private fun CfnTemplateGenerator.processColumns(
        ksName: Identifier,
        columnDefinitions: Map<Identifier, Type>,
        staticColumnNames: List<Identifier>,
        partitionKeyColumnNames: List<Identifier>,
        clusteringKeyColumnNames: List<Identifier>,
        clusteringOrders: Map<Identifier, TableProperties.ClusteringOrder>,
        tableBuilder: CfnTable.Builder
    ) {
        val allColumnDefinitions = columnDefinitions.toMutableMap()
        partitionKeyColumnNames.map { columnName ->
            val columnType = allColumnDefinitions.remove(columnName)!!
            CfnTable.ColumnProperty.builder()
                .columnName(columnName.name)
                .columnType(getTypeNameOrRef(ksName, columnType))
                .build()
        }.also { tableBuilder.partitionKeyColumns(it) }
        clusteringKeyColumnNames.map { columnName ->
            val columnType = allColumnDefinitions.remove(columnName)!!
            CfnTable.ClusteringKeyColumnProperty.builder()
                .column(CfnTable.ColumnProperty.builder()
                    .columnName(columnName.name)
                    .columnType(getTypeNameOrRef(ksName, columnType))
                    .build())
                .orderBy(clusteringOrders[columnName]?.name)
                .build()
        }.also { tableBuilder.clusteringKeyColumns(it) }
        if (staticColumnNames.isNotEmpty()) {
            Logger.error("Static columns are not supported by Keyspaces yet")
        }
        allColumnDefinitions.entries.map { (columnName, columnType) ->
            CfnTable.ColumnProperty.builder()
                .columnName(columnName.name)
                .columnType(getTypeNameOrRef(ksName, columnType))
                .build()
        }.also { tableBuilder.regularColumns(it) }
    }

    private fun CfnTemplateGenerator.getTypeNameOrRef(ksName: Identifier, type: Type): String =
        when (type) {
            is Type.Simple ->
                type.name
            is Type.Composite ->
                Fn.sub("${type.kind}<\${Types}>",
                    mapOf("Types" to Fn.join(",", type.elementTypes.map { getTypeNameOrRef(ksName, it) })))
            is Type.User -> {
                if (type.ksName != null && type.ksName != ksName) {
                    Logger.error("Cannot reference user type '${type.utName}' " +
                            "in another keyspace '${type.ksName}'")
                }
                createdTypes[Pair(ksName, type.utName)]?.let { Fn.select(1, Fn.split("|", it.ref)) }
                    ?: type.utName.name.also {
                        Logger.warn("Attempted to reference user type '${type.utName}' before creating it first")
                    }
                Logger.error("User types are not supported by Keyspaces yet")
            }
            is Type.Custom ->
                Logger.error("Custom types are not supported by Keyspaces")
        }

    private fun CfnTemplateGenerator.processRegularProperties(
        regularProperties: PropertyMap<Identifier, Any>,
        table: CfnTable
    ) {
        regularProperties.forEach { (key, value) ->
            when (key.name) {
                "comment" -> {
                    @Suppress("UNUSED_VARIABLE")
                    val comment = value as? String
                        ?: Logger.error("Invalid value for property 'comments'; it should be a string")
                    Logger.error("Property 'comment' currently cannot be set via CloudFormation")
                }
                "default_time_to_live" -> {
                    @Suppress("UNUSED_VARIABLE")
                    val defaultTimeToLive = (value as? String)?.toIntOrNull()
                        ?: Logger.error("Invalid value for property 'default_time_to_live'; " +
                                "it should be an integer")
                    Logger.error("Property 'default_time_to_live' is not supported by Keyspaces yet")
                }
                "tags" -> {
                    @Suppress("UNCHECKED_CAST")
                    val tags = value as? PropertyMap<String, String>
                        ?: Logger.error("Invalid value for property 'tags'; it should be a map")
                    Tags.of(table).apply {
                        tags.forEach { add(it.key, it.value) }
                    }
                    Logger.error("Property 'tags' currently cannot be set via CloudFormation")
                }
                else ->
                    Logger.warn("Property '$key' is not applicable to Keyspaces")
            }
        }
    }

    private fun CfnTemplateGenerator.processCustomProperties(
        customProperties: PropertyMap<String, PropertyMap<String, String>>?,
        table: CfnTable
    ) {
        customProperties?.forEach { (key, value) ->
            when (key) {
                "capacity_mode" -> {
                    val capacityMode = value.toMutableMap()
                    when (val throughputMode = capacityMode.remove("throughput_mode")) {
                        null ->
                            Logger.error("Missing required option 'throughput_mode' " +
                                    "of custom property 'capacity_mode'")
                        "PAY_PER_REQUEST" -> {
                            if (capacityMode.isNotEmpty()) {
                                Logger.error("Unrecognized option '${capacityMode.keys.first()}' " +
                                        "for capacity mode throughput mode 'PAY_PER_REQUEST'")
                            }
                            table.setBillingMode(CfnTable.BillingModeProperty.builder()
                                .mode("ON_DEMAND")
                                .build())
                        }
                        "PROVISIONED" -> {
                            val readCapacityUnits = capacityMode.remove("read_capacity_units")?.let {
                                it.toIntOrNull()
                                    ?: Logger.error("Invalid value for option 'read_capacity_units' " +
                                                "of custom property 'capacity_mode'; it should be an integer")
                            } ?: Logger.error("Missing required option 'read_capacity_units' " +
                                        "for capacity mode throughput mode 'PROVISIONED'")
                            val writeCapacityUnits = capacityMode.remove("write_capacity_units")?.let {
                                it.toIntOrNull()
                                    ?: Logger.error("Invalid value for option 'write_capacity_units' " +
                                            "of custom property 'capacity_mode'; it should be an integer")
                            } ?: Logger.error("Missing required option 'write_capacity_units' " +
                                    "for capacity mode throughput mode 'PROVISIONED'")
                            if (capacityMode.isNotEmpty()) {
                                Logger.error("Unrecognized option '${capacityMode.keys.first()}' " +
                                        "for capacity mode throughput mode 'PROVISIONED'")
                            }
                            table.setBillingMode(CfnTable.BillingModeProperty.builder()
                                .mode("PROVISIONED")
                                .provisionedThroughput(CfnTable.ProvisionedThroughputProperty.builder()
                                    .readCapacityUnits(readCapacityUnits)
                                    .writeCapacityUnits(writeCapacityUnits)
                                    .build())
                                .build())
                        }
                        else ->
                            Logger.error("Capacity mode throughput mode '$throughputMode' " +
                                    "is not recognized by Keyspaces")
                    }
                }
                "point_in_time_recovery" -> {
                    val pointInTimeRecovery = value.toMutableMap()
                    @Suppress("UNUSED_VARIABLE")
                    val enabled = when (val status = pointInTimeRecovery.remove("status")) {
                        null ->
                            Logger.error("Missing required option 'status' of custom property 'point_in_time_recovery'")
                        "enabled" ->
                            true
                        "disabled" ->
                            false
                        else ->
                            Logger.error("Point in time recovery status '$status' is not recognized by Keyspaces")
                    }
                    if (pointInTimeRecovery.isNotEmpty()) {
                        Logger.error("Unrecognized option '${pointInTimeRecovery.keys.first()}' " +
                                "of custom property 'point_in_time_recovery'")
                    }
                    Logger.error("Custom property 'point_in_time_recovery' currently cannot be set via CloudFormation")
                }
                else ->
                    Logger.error("Custom property '$key' is not recognized by Keyspaces")
            }
        }
    }

    private fun CfnTemplateGenerator.processCompactStorage(
        compactStorage: Boolean
    ) {
        if (compactStorage) {
            Logger.warn("COMPACT STORAGE is not applicable to Keyspaces")
        }
    }
}
