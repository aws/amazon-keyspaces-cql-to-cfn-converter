# IMPORTANT: Latest Version

The current version is 1.0.0. Please see the [changelog](./CHANGELOG.md) for details on version history.

# Overview

This package implements a command-line tool for converting Apache Cassandra Query Language (CQL) scripts to AWS CloudFormation (CFN) templates, which allows Amazon Keyspaces (for Apache Cassandra) schema to be easily managed in AWS CloudFormation stacks.

The tool currently supports the following statements:

* `CREATE KEYSPACE`
* `CREATE TABLE`
* `USE`

# Example Usage

Given a CQL script named `my_cql_script.cql`

```sql
CREATE KEYSPACE my_keyspace WITH replication = {'class': 'SingleRegionStrategy'};
USE my_keyspace;
CREATE TABLE my_table (pk int PRIMARY KEY);
```

run the tool by executing

```sh
$ cql2cfn my_cql_script.cql my_cfn_template.json
```

this will produce a CFN template named `my_cfn_template.json`


```json
{
  "Resources": {
    "Keyspace1": {
      "Type": "AWS::Cassandra::Keyspace",
      "Properties": {
        "KeyspaceName": "my_keyspace"
      }
    },
    "Table1": {
      "Type": "AWS::Cassandra::Table",
      "Properties": {
        "KeyspaceName": {
          "Ref": "Keyspace1"
        },
        "PartitionKeyColumns": [
          {
            "ColumnName": "pk",
            "ColumnType": "int"
          }
        ],
        "ClusteringKeyColumns": [],
        "RegularColumns": [],
        "TableName": "my_table"
      }
    }
  }
}
```

The tool requires one argument, the path to the CQL script to be converted. An optional second argument can be supplied to specify the path to the generated CFN template; otherwise, the generated CFN template is printed out to the standard output.

By default, the tool operates in relaxed mode, in which case it only gives warnings about the following issues:

* A property specified for a keyspace/table is not applicable to or supported by Keyspaces
* A keyspace/table is created a second time without `IF NOT EXISTS`
* A keyspace is referenced without being created before

These warnings can be turned into errors by supplying the `--strict` option to the tool.

A help message can be printed out by using the `--help` option.

# How to build the tool

The tool needs to be built from source by running the following on Linux/MacOS...

```sh
./gradlew installDist
```

on Microsoft Windows, run... 

```bat
gradlew.bat installDist
```

JDK 11 or later is required for building the tool.

After it is built successfully, the executable can be found in `./build/install/cql2cfn/bin/cql2cfn`.


## Testing
To execute tests on Linux/MacOS use...

```sh
./gradlew test
```

On Microsoft Windows use... 

```bat
gradlew.bat test
```


