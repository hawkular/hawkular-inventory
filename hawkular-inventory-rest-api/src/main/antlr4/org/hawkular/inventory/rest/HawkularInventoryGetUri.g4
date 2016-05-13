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
grammar HawkularInventoryGetUri ;

PROPERTY_NAME : 'propertyName' ;
PROPERTY_VALUE : 'propertyValue' ;
NAME : 'name' ;
TYPE : 'type' ;
ID : 'id' ;
CP : 'cp' ;
IDENTICAL : 'identical' ;
SOURCE_TYPE : 'sourceType' ;
TARGET_TYPE : 'targetType' ;
DEFINED_BY : 'definedBy' ;
RECURSIVE : 'recursive' ;
OVER : 'over' ;
RELATED_BY : 'relatedBy' ;
RELATED_TO : 'relatedTo' ;
RELATED_WITH : 'relatedWith' ;

//different lexer tokens we recognize in the URLs
ENTITY_TYPE : 't' | 'e' | 'mp' | 'f' | 'rt' | 'mt' | 'ot' | 'r' | 'm' | 'd' ;
REL_TYPE : 'rl' ;
ENTITIES : 'entities' ;
RELATIONSHIPS : 'relationships' ;
DIRECTION : 'in' | 'out' ;// | 'both' ; NOTE we don't support "both" because making the traversal behave right would be
                          //            very expensive. Imagine grelim: v.bothE().bothV() - the result would contain
                          //            the "v" vertex (or vertices) n-times, where n is the number of edges connected
                          //            to v. To give an expected result, one would have to add ".dedup()" pipe after
                          //            that to remove the duplicities, but that means holding the whole result set
                          //            in memory. Given the little utility of "both" direction, I opted to leave it out
                          //            from the URL queries.

//fallback to catch any character safe to be present in an URI path
URI_SAFE : [a-zA-Z0-9%\-\._~:@]+ ;

//These 3 are the same bug give different semantic meaning. They can have any value which means they can possess
//any token recognized by the lexer.
value : PROPERTY_NAME | PROPERTY_VALUE | NAME | TYPE | ID | CP | IDENTICAL | SOURCE_TYPE | TARGET_TYPE | ENTITY_TYPE
        | REL_TYPE | ENTITIES | RELATIONSHIPS | DIRECTION | DEFINED_BY | RECURSIVE
        | OVER | RELATED_BY | RELATED_TO | RELATED_WITH | URI_SAFE ;
id : PROPERTY_NAME | PROPERTY_VALUE | NAME | TYPE | ID | CP | IDENTICAL | SOURCE_TYPE | TARGET_TYPE | ENTITY_TYPE
        | REL_TYPE | ENTITIES | RELATIONSHIPS | DIRECTION | DEFINED_BY | RECURSIVE
        | OVER | RELATED_BY | RELATED_TO | RELATED_WITH | URI_SAFE ;
name : PROPERTY_NAME | PROPERTY_VALUE | NAME | TYPE | ID | CP | IDENTICAL | SOURCE_TYPE | TARGET_TYPE | ENTITY_TYPE
        | REL_TYPE | ENTITIES | RELATIONSHIPS | DIRECTION | DEFINED_BY | RECURSIVE
        | OVER | RELATED_BY | RELATED_TO | RELATED_WITH | URI_SAFE ;

uri
    : pathContinuation EOF
    | relationshipAsFirstSegment EOF
    | recursiveContinuation EOF
    | pathEnd EOF
    | EOF
    ;

entityType
    : ENTITY_TYPE
    ;

relationshipType
    : REL_TYPE
    ;

relationshipAsFirstSegment
    : '/' relationshipType ';' id (';' relationshipDirection)? (';' relationshipFilterSpec)*
      (relationshipEnd | pathLikeContinuation)
    ;

relationshipContinuation
    : '/' relationshipType ';' name (';' relationshipDirection)? (';' relationshipFilterSpec)*
      (relationshipEnd | pathLikeContinuation)
    ;

pathContinuation
    : '/' entityType ';' id (';' filterSpec)* (pathEnd | pathLikeContinuation)?
    | '/' filterSpec (';' filterSpec)* (pathEnd | pathLikeContinuation)?
    ;

recursiveContinuation
    :'/' RECURSIVE (';' recursiveFilterSpec)? (';' filterSpec)* (pathEnd | pathLikeContinuation)?
    ;

identicalContinuation
    : '/' IDENTICAL (pathEnd | pathLikeContinuation)?
    ;

pathLikeContinuation
    : pathContinuation
    | recursiveContinuation
    | identicalContinuation
    | relationshipContinuation
    ;

relationshipEnd
    : '/' RELATIONSHIPS
    | '/' ENTITIES
    ;

pathEnd
    : '/' ENTITIES (';' filterSpec)*
    | '/' RELATIONSHIPS (';' relationshipDirection)? (';' relationshipFilterSpec)*
    ;

filterSpec
    : filterName ('=' value)?
    ;

filterName
    : TYPE | ID | NAME | CP | PROPERTY_NAME | PROPERTY_VALUE | DEFINED_BY | RELATED_BY | RELATED_TO | RELATED_WITH
    ;

relationshipFilterSpec
    : relationshipFilterName '=' value
    ;

relationshipFilterName
    : ID | PROPERTY_NAME | PROPERTY_VALUE | NAME | SOURCE_TYPE | TARGET_TYPE
    ;

relationshipDirection
    : DIRECTION
    ;

recursiveFilterSpec
    : OVER '=' value
    ;
