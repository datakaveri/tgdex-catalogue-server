package org.cdpg.dx.tgdex.apiserver;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.tgdex.item.controller.ItemController;
import org.cdpg.dx.tgdex.item.factory.ItemControllerFactory;
import org.cdpg.dx.tgdex.list.controller.ListController;
import org.cdpg.dx.tgdex.list.factory.ListControllerFactory;
import org.cdpg.dx.tgdex.search.controller.SearchController;
import org.cdpg.dx.tgdex.search.factory.SearchControllerFactory;
import org.cdpg.dx.catalogue.service.CatalogueService;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.tgdex.validator.service.ValidatorService;

import java.util.List;

import static org.cdpg.dx.common.config.ServiceProxyAddressConstants.*;
import static org.cdpg.dx.tgdex.util.Constants.VALIDATOR_SERVICE_ADDRESS;

public class ControllerFactory {
  private static final Logger LOGGER = LogManager.getLogger(ControllerFactory.class);

  private ControllerFactory() {}

  public static List<ApiController> createControllers(Vertx vertx, JsonObject config) {
    LOGGER.info("Creating controllers...");
    // Service proxies
    final ElasticsearchService esService =
        ElasticsearchService.createProxy(vertx, ELASTIC_SERVICE_ADDRESS);
    final DataBrokerService brokerService =
        DataBrokerService.createProxy(vertx, DATA_BROKER_SERVICE_ADDRESS);
    final CatalogueService catService =
        CatalogueService.createProxy(vertx, CATALOGUE_SERVICE_ADDRESS);
    final ValidatorService validatorService =
        ValidatorService.createProxy(vertx, VALIDATOR_SERVICE_ADDRESS);
    final AuditingHandler auditingHandler = new AuditingHandler(brokerService);
    final String docIndex = config.getString("docIndex");
    final ItemController crudController = ItemControllerFactory.createCrudController(auditingHandler,esService);
    final ListController listController = ListControllerFactory.createListController(esService, auditingHandler, docIndex,validatorService);
    final SearchController searchController = SearchControllerFactory.createSearchController(esService, auditingHandler,docIndex, validatorService);
    return List.of(crudController,listController,searchController);
  }
}
