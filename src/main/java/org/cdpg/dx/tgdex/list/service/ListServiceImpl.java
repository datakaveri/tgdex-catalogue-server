package org.cdpg.dx.tgdex.list.service;

import static org.cdpg.dx.database.elastic.util.Constants.*;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.tgdex.search.util.ResponseModel;
import org.cdpg.dx.tgdex.validator.service.ValidatorService;

public class ListServiceImpl implements ListService {
  private static final Logger LOGGER = LogManager.getLogger(ListServiceImpl.class);
  private final QueryDecoder queryDecoder;
  ElasticsearchService elasticsearchService;
  String docIndex;

  public ListServiceImpl(
      ElasticsearchService elasticsearchService,
      String docIndex,
      ValidatorService validatorService) {
    this.elasticsearchService = elasticsearchService;
    this.queryDecoder = new QueryDecoder();
    this.docIndex = docIndex;
  }

  @Override
  public Future<ResponseModel> getAvailableFilters(QueryDecoderRequestDTO queryDecoderRequestDTO) {

    if (queryDecoderRequestDTO.getFilter() == null
        || queryDecoderRequestDTO.getFilter().isEmpty()) {
      return Future.failedFuture("Missing or empty 'filter' array");
    }
    QueryDecoder queryDecoder = new QueryDecoder();
    QueryModel queryModel = queryDecoder.listMultipleItemTypesQuery(queryDecoderRequestDTO);
    return elasticsearchService
        .search(docIndex, queryModel, AGGREGATION_LIST)
        .map(ResponseModel::new)
        .onFailure(err -> LOGGER.error("Search execution failed: {}", err.getMessage()));
  }
}
