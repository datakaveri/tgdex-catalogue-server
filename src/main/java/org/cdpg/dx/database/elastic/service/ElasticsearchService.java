package org.cdpg.dx.database.elastic.service;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.database.elastic.model.QueryModel;

import java.util.List;
import java.util.Map;

@VertxGen
@ProxyGen
public interface ElasticsearchService {

    @GenIgnore
    static ElasticsearchService createProxy(Vertx vertx, String address) {
        return new ElasticsearchServiceVertxEBProxy(vertx, address);
    }

    Future<List<ElasticsearchResponse>> search(String index, QueryModel queryModel, String options);

    Future<Integer> count(String index, QueryModel queryModel);

    Future<List<String>> createDocuments(String index, List<QueryModel> documentModels);
    Future<Void> deleteByQuery(String index,QueryModel queryModel);
    Future<Void> deleteDocument(String index, String id);
    Future<ElasticsearchResponse> getSingleDocument(QueryModel queryModel, String docIndex);
    Future<Void> updateDocument(String index, String id, QueryModel documentModel);
}
