package org.cdpg.dx.tgdex.apiserver;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.tgdex.crud.controller.CrudController;
import org.cdpg.dx.tgdex.crud.factory.CrudControllerFactory;
import org.cdpg.dx.tgdex.list.controller.ListController;
import org.cdpg.dx.tgdex.list.factory.ListControllerFactory;
import org.cdpg.dx.tgdex.search.controller.SearchController;
import org.cdpg.dx.tgdex.search.factory.SearchControllerFactory;
import org.cdpg.dx.catalogue.service.CatalogueService;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.revoked.service.RevokedService;
import org.cdpg.dx.uniqueattribute.service.UniqueAttributeService;
import java.util.List;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.*;

public class ControllerFactory {
  private static final Logger LOGGER = LogManager.getLogger(ControllerFactory.class);

  private ControllerFactory() {}

  public static List<ApiController> createControllers(Vertx vertx, JsonObject config) {
    LOGGER.info("Creating controllers...");
    // Service proxies
    final PostgresService pgService = PostgresService.createProxy(vertx, POSTGRES_SERVICE_ADDRESS);
    final ElasticsearchService esService =
        ElasticsearchService.createProxy(vertx, ELASTIC_SERVICE_ADDRESS);
    final DataBrokerService brokerService =
        DataBrokerService.createProxy(vertx, DATA_BROKER_SERVICE_ADDRESS);
    final CatalogueService catService =
        CatalogueService.createProxy(vertx, CATALOGUE_SERVICE_ADDRESS);
    final RevokedService revokedService =
        RevokedService.createProxy(vertx, REVOKED_SERVICE_ADDRESS);
    final UniqueAttributeService uniqueAttrService =
        UniqueAttributeService.createProxy(vertx, UNIQUE_ATTRIBUTE_SERVICE_ADDRESS);
    final AuditingHandler auditingHandler = new AuditingHandler(vertx);

    final CrudController crudController = CrudControllerFactory.createCrudController(auditingHandler,esService);
    final ListController listController = ListControllerFactory.createListController(esService, auditingHandler);
    final SearchController searchController = SearchControllerFactory.createSearchController(esService, auditingHandler);
    return List.of(crudController,listController,searchController);
  }
}
