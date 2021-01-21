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

class PropertyMap<K, V> : LinkedHashMap<K, V>() {
    fun addProperty(key: K, value: V) {
        if (containsKey(key)) {
            throw ParsingException("multiple definition for property '$key'");
        }
        put(key, value)
    }
}

interface Properties {
    val regularProperties: PropertyMap<Identifier, Any>
    var customProperties: PropertyMap<String, PropertyMap<String, String>>?

    fun validate()

    fun defineCustomProperties(customProperties: PropertyMap<String, PropertyMap<String, String>>) {
        if (this.customProperties != null) {
            throw ParsingException("multiple definition for property 'custom_properties'")
        }
        this.customProperties = customProperties
    }
}

data class KeyspaceProperties @JvmOverloads constructor(
    override val regularProperties: PropertyMap<Identifier, Any> = PropertyMap(),
    override var customProperties: PropertyMap<String, PropertyMap<String, String>>? = null
) : Properties {
    override fun validate() {
    }
}

data class TableProperties @JvmOverloads constructor(
    override val regularProperties: PropertyMap<Identifier, Any> = PropertyMap(),
    override var customProperties: PropertyMap<String, PropertyMap<String, String>>? = null,
    val columnDefinitions: MutableMap<Identifier, Type> = LinkedHashMap(),
    val staticColumnNames: MutableList<Identifier> = ArrayList(),
    val partitionKeyColumnNames: MutableList<Identifier> = ArrayList(),
    val clusteringKeyColumnNames: MutableList<Identifier> = ArrayList(),
    val clusteringOrders: MutableMap<Identifier, ClusteringOrder> = LinkedHashMap(),
    var compactStorage: Boolean = false
) : Properties {
    override fun validate() {
        if (partitionKeyColumnNames.isEmpty()) {
            throw ParsingException("no PRIMARY KEY specified; exactly one required")
        }
        if (clusteringKeyColumnNames.isEmpty() && staticColumnNames.isNotEmpty()) {
            throw ParsingException("static columns not allowed if table has no clustering column")
        }
    }

    fun addColumnDefinition(columnName: Identifier, columnType: Type) {
        if (columnDefinitions.containsKey(columnName)) {
            throw ParsingException("multiple definition of column '$columnName'")
        }
        columnDefinitions[columnName] = columnType
    }

    fun addStaticColumnName(columnName: Identifier) {
        staticColumnNames.add(columnName)
    }

    fun startDefiningPartitionKeyColumnNames() {
        if (partitionKeyColumnNames.isNotEmpty()) {
            throw ParsingException("multiple definition of PRIMARY KEY; exactly one required")
        }
    }

    fun addPartitionKeyColumnName(columnName: Identifier) {
        addKeyColumnName(columnName, partitionKeyColumnNames)
    }

    fun addClusteringColumnName(columnName: Identifier) {
        addKeyColumnName(columnName, clusteringKeyColumnNames)
    }

    private fun addKeyColumnName(columnName: Identifier, keyColumnNames: MutableList<Identifier>) {
        if (!columnDefinitions.containsKey(columnName)) {
            throw ParsingException("unknown column '$columnName' referenced in PRIMARY KEY")
        }
        if (staticColumnNames.contains(columnName)) {
            throw ParsingException("static column '$columnName' referenced in PRIMARY KEY")
        }
        if (partitionKeyColumnNames.contains(columnName) || clusteringKeyColumnNames.contains(columnName)) {
            throw ParsingException("column '$columnName' referenced more than once in PRIMARY KEY")
        }
        keyColumnNames.add(columnName)
    }

    fun startDefiningClusteringOrder() {
        if (clusteringOrders.isNotEmpty()) {
            throw ParsingException("multiple definition of CLUSTERING ORDER")
        }
    }

    fun addClusteringAscendingOrder(columnName: Identifier) {
        addClusteringOrder(columnName, ClusteringOrder.ASC)
    }

    fun addClusteringDescendingOrder(columnName: Identifier) {
        addClusteringOrder(columnName, ClusteringOrder.DESC)
    }

    private fun addClusteringOrder(columnName: Identifier, clusteringOrder: ClusteringOrder) {
        val position = clusteringKeyColumnNames.indexOf(columnName)
        if (position < 0) {
            throw ParsingException("only clustering key columns can be referenced in CLUSTERING ORDER")
        }
        if (clusteringOrders.containsKey(columnName)) {
            throw ParsingException("column '$columnName' referenced more than once in CLUSTERING ORDER")
        }
        if (clusteringOrders.size != position) {
            throw ParsingException("missing CLUSTERING ORDER " +
                    "for column '${clusteringKeyColumnNames[clusteringOrders.size]}'")
        }
        clusteringOrders[columnName] = clusteringOrder
    }

    fun defineCompactStorage() {
        if (compactStorage) {
            throw ParsingException("multiple definition of COMPACT STORAGE")
        }
        compactStorage = true
    }

    enum class ClusteringOrder {
        ASC,
        DESC
    }
}
