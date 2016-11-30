/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
createKeyspace {
    version '2.0.0.0'
    name keyspace
    author 'Lukas Krejci'
    tags '2.0.0'
    description 'Ensure our keyspace exists.'
}

schemaChange {
    version '2.0.0.1'
    author 'Lukas Krejci'
    tags '2.0.0'
    description 'Create sysconfig table.'
    cql """
CREATE TABLE sys_config (
    config_id text,
    name text,
    value text,
    PRIMARY KEY (config_id, name)
) WITH compaction = { 'class': 'LeveledCompactionStrategy' }
"""
}

schemaChange {
    version '2.0.0.2'
    author 'Lukas Krejci'
    tags '2.0.0'
    description 'Create initial tables for entities.'
    cql (["""
CREATE TABLE entity (
    cp text,
    name text,
    properties map<text, text>,
    PRIMARY KEY (cp)
) WITH compaction = { 'class': 'LeveledCompactionStrategy' };
""","""
CREATE INDEX entity_name ON entity ( name );
""","""
CREATE INDEX entity_property ON entity (KEYS(properties));
"""])
}

schemaChange {
    version '2.0.0.3'
    author 'Lukas Krejci'
    tags '2.0.0'
    description 'Tables for relationships'
    cql (["""
CREATE TABLE relationship (
    cp text PRIMARY KEY,
    name text,
    source_cp text,
    target_cp text,
    properties map<text, text>
) WITH compaction = { 'class': 'LeveledCompactionStrategy' };
""", """
CREATE TABLE relationship_out (
    source_cp text,
    target_cp text,
    cp text,
    name text,
    properties map<text, text>,
    PRIMARY KEY (source_cp, name)
) WITH compaction = { 'class': 'LeveledCompactionStrategy' }; 
""", """
CREATE INDEX relationship_out_property ON relationship_out (KEYS(properties));  
""", """
CREATE INDEX relationship_out_cp ON relationship_out (cp);  
""", """
CREATE TABLE relationship_in (
    target_cp text,
    source_cp text,
    cp text,
    name text,
    properties map<text, text>,
    PRIMARY KEY (target_cp, name)
) WITH compaction = { 'class': 'LeveledCompactionStrategy' }; 
""", """
CREATE INDEX relationship_in_property ON relationship_in (KEYS(properties));  
""", """
CREATE INDEX relationship_in_cp ON relationship_in (cp);  
"""])
}
