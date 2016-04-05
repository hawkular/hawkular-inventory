--
-- Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
-- and other contributors as indicated by the @author tags.
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--    http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE %VERTICES% (
  id SERIAL NOT NULL PRIMARY KEY
);

CREATE TABLE %EDGES% (
  id SERIAL NOT NULL PRIMARY KEY,
  vertex_out INT NOT NULL,
  vertex_in INT NOT NULL,
  label CHARACTER VARYING (255) NOT NULL,
  CONSTRAINT fk_vertex_out FOREIGN KEY (vertex_out) REFERENCES %VERTICES% (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_vertex_in FOREIGN KEY (vertex_in) REFERENCES %VERTICES% (id)
    ON DELETE CASCADE
);

CREATE INDEX idx_edge_labels ON %EDGES% (label);

CREATE TABLE %VERTEX_PROPERTIES% (
  vertex_id INT NOT NULL,
  name CHARACTER VARYING(255) NOT NULL,
  string_value TEXT,
  numeric_value NUMERIC,
  value_type SMALLINT NOT NULL,
  CONSTRAINT fk_vertex FOREIGN KEY (vertex_id) REFERENCES %VERTICES% (id)
    ON DELETE CASCADE,
  UNIQUE (vertex_id, name)
);

CREATE TABLE %VERTEX_PROPERTIES%_uq (
  vertex_id INT NOT NULL,
  name CHARACTER VARYING(255) NOT NULL,
  string_value TEXT,
  numeric_value NUMERIC,
  value_type SMALLINT NOT NULL,
  CONSTRAINT fk_vertex_uq FOREIGN KEY (vertex_id) REFERENCES %VERTICES% (id)
    ON DELETE CASCADE,
  UNIQUE (name, string_value, value_type),
  UNIQUE (name, numeric_value, value_type)
);

CREATE INDEX idx_%VERTEX_PROPERTIES% ON %VERTEX_PROPERTIES% (name);
CREATE INDEX idx_%VERTEX_PROPERTIES%_2 ON %VERTEX_PROPERTIES% (name, string_value);
CREATE INDEX idx_%VERTEX_PROPERTIES%_3 ON %VERTEX_PROPERTIES% (name, numeric_value);

CREATE INDEX idx_%VERTEX_PROPERTIES%_uq ON %VERTEX_PROPERTIES%_UQ (name);
CREATE INDEX idx_%VERTEX_PROPERTIES%_uq_2 ON %VERTEX_PROPERTIES%_UQ (name, string_value);
CREATE INDEX idx_%VERTEX_PROPERTIES%_uq_3 ON %VERTEX_PROPERTIES%_UQ (name, numeric_value);

CREATE TABLE %EDGE_PROPERTIES% (
  edge_id INT NOT NULL,
  name CHARACTER VARYING(255) NOT NULL,
  string_value TEXT,
  numeric_value NUMERIC,
  value_type SMALLINT NOT NULL,
  CONSTRAINT fk_edge FOREIGN KEY (edge_id) REFERENCES %EDGES% (id)
    ON DELETE CASCADE,
  UNIQUE (edge_id, name)
);

CREATE TABLE %EDGE_PROPERTIES%_uq (
  edge_id INT NOT NULL,
  name CHARACTER VARYING(255) NOT NULL,
  string_value TEXT,
  numeric_value NUMERIC,
  value_type SMALLINT NOT NULL,
  CONSTRAINT fk_edge_uq FOREIGN KEY (edge_id) REFERENCES %EDGES% (id)
    ON DELETE CASCADE,
  UNIQUE (name, string_value, value_type),
  UNIQUE (name, numeric_value, value_type)
);

CREATE INDEX idx_%EDGE_PROPERTIES% ON %EDGE_PROPERTIES% (name);
CREATE INDEX idx_%EDGE_PROPERTIES%_2 ON %EDGE_PROPERTIES% (name, string_value);
CREATE INDEX idx_%EDGE_PROPERTIES%_3 ON %EDGE_PROPERTIES% (name, numeric_value);

CREATE INDEX idx_%EDGE_PROPERTIES%_uq ON %EDGE_PROPERTIES%_UQ (name);
CREATE INDEX idx_%EDGE_PROPERTIES%_uq_2 ON %EDGE_PROPERTIES%_UQ (name, string_value);
CREATE INDEX idx_%EDGE_PROPERTIES%_uq_3 ON %EDGE_PROPERTIES%_UQ (name, numeric_value);

CREATE TABLE %VERTICES%_uidxs (
  name VARCHAR(255) NOT NULL PRIMARY KEY
);

CREATE TABLE %EDGES%_uidxs (
  name VARCHAR(255) NOT NULL PRIMARY KEY
);
