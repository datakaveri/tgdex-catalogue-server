package org.cdpg.dx.database.elastic.util;

public enum QueryType {
  MATCH_ALL,
  MATCH,
  TERM,
  TERMS,
  SHOULD,
  BOOL,
  RANGE,
  WILDCARD,
  GEO_SHAPE,
  GEO_BOUNDING_BOX,
  TEXT,
  SCRIPT_SCORE,
  MATCH_PHRASE,
  MULTI_MATCH,
  QUERY_STRING
}
