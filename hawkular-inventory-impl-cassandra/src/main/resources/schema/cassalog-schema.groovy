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
    cp ascii,
    id text,
    type int,
    name text,
    properties map<text, text>,
    PRIMARY KEY (cp)
) WITH compaction = { 'class': 'LeveledCompactionStrategy' };
""","""
CREATE TABLE entity_id_idx (
    id text,
    cp ascii,
    PRIMARY KEY (id, cp)
) WITH compaction = { 'class': 'LeveledCompactionStrategy' };
""","""
CREATE TABLE entity_type_idx (
    type int,
    cp ascii,
    PRIMARY KEY (type, cp)
) WITH compaction = { 'class': 'LeveledCompactionStrategy' };
""","""
CREATE TABLE entity_name_idx (
    name text,
    cp set<ascii>,
    PRIMARY KEY (name)
) WITH compaction = { 'class': 'LeveledCompactionStrategy' };
"""])
}

schemaChange {
    version '2.0.0.3'
    author 'Lukas Krejci'
    tags '2.0.0'
    description 'Tables for relationships'
    cql (["""
CREATE TABLE relationship (
    cp ascii PRIMARY KEY,
    name text,
    source_cp ascii,
    target_cp ascii,
    properties map<text, text>
) WITH compaction = { 'class': 'LeveledCompactionStrategy' };
""", """
CREATE TABLE relationship_out (
    source_cp ascii,
    target_cp ascii,
    source_id text,
    target_id text,
    source_type int,
    target_type int,
    cp ascii,
    name text,
    properties map<text, text>,
    PRIMARY KEY (source_cp, name)
) WITH compaction = { 'class': 'LeveledCompactionStrategy' }; 
""", """
CREATE TABLE relationship_in (
    target_cp ascii,
    source_cp ascii,
    source_id text,
    target_id text,
    source_type int,
    target_type int,
    cp ascii,
    name text,
    properties map<text, text>,
    PRIMARY KEY (target_cp, name)
) WITH compaction = { 'class': 'LeveledCompactionStrategy' }; 
"""])
}
