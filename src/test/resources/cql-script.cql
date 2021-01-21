/*
 * Create a Keyspace
 */
CREATE KEYSPACE IF NOT EXISTS ks
WITH replication = {'class': 'SingleRegionStrategy'} -- Use Keyspaces-specific replication strategy
;

/*
 * Create a Table
 */
CREATE TABLE IF NOT EXISTS ks.t1 (
    pk int PRIMARY KEY,
    my_text text,
    my_float float,
    my_double double
)
WITH custom_properties = {
    'capacity_mode': {
        'throughput_mode': 'PROVISIONED',
        'read_capacity_units': '10',
        'write_capacity_units': '5'
    }
}
;

/*
 * Use a Keyspace
 */
USE ks
;

/*
 * Create another Table
 */
CREATE TABLE IF NOT EXISTS t2 (
    pk1 text,
    pk2 int,
    ck1 text,
    ck2 int,
    my_list list<decimal>,
    my_set set<decimal>,
    my_map map<decimal, decimal>,
    PRIMARY KEY ((pk1, pk2), ck1, ck2)
)
WITH CLUSTERING ORDER BY (ck1 DESC, ck2 ASC)
// Do not use provisioned capacity
;