package org.cdpg.dx.tgdex.search.service;

import io.vertx.core.Future;
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;
import org.cdpg.dx.tgdex.search.util.ResponseModel;

public interface SearchService {

  Future<ResponseModel> postSearch(QueryDecoderRequestDTO queryDecoder);

  Future<ResponseModel> postCount(QueryDecoderRequestDTO queryDecoder);
}
