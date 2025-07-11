package org.cdpg.dx.catalogue.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.catalogue.client.CatalogueClient;
import org.cdpg.dx.common.exception.DxBadRequestException;

import java.util.concurrent.TimeUnit;

import static org.cdpg.dx.common.ErrorMessage.BAD_REQUEST_ERROR;

public class CatalogueServiceImpl implements CatalogueService {
  private static final Logger LOGGER = LogManager.getLogger(CatalogueServiceImpl.class);
  private final Cache<String, JsonObject> catalogueCache =
      CacheBuilder.newBuilder().maximumSize(10000).expireAfterWrite(1L, TimeUnit.DAYS).build();
  private final Cache<String, String> providerOwnerCache =
      CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(1L, TimeUnit.DAYS).build();
  private final CatalogueClient catalogueClient;

  public CatalogueServiceImpl(Vertx vertx, CatalogueClient catalogueClient) {
    this.catalogueClient = catalogueClient;
    refreshCatalogue();
    vertx.setPeriodic(
        TimeUnit.HOURS.toMillis(1),
        handler -> {
          refreshCatalogue();
        });
  }

  @Override
  public Future<JsonObject> fetchCatalogueInfo(String id) {
    Promise<JsonObject> promise = Promise.promise();
    LOGGER.trace("request for id : {}", id);
    if (catalogueCache.getIfPresent(id) != null) {
      return Future.succeededFuture(catalogueCache.getIfPresent(id));
    } else {
      idCatalogueInfo(id)
          .onSuccess(
              successHandler -> {
                if (catalogueCache.getIfPresent(id) != null) {
                  promise.complete(catalogueCache.getIfPresent(id));
                } else {
                  LOGGER.info("id :{} not found in catalogue server", id);
                  promise.fail(new DxBadRequestException(BAD_REQUEST_ERROR));
                }
              })
          .onFailure(promise::fail);
    }
    return promise.future();
  }

  @Override
  public Future<String> getProviderOwnerId(String id) {
    Promise<String> promise = Promise.promise();
    if (providerOwnerCache.getIfPresent(id) != null) {
      return Future.succeededFuture(providerOwnerCache.getIfPresent(id));
    } else {
      providerOwnerInfo(id)
          .onSuccess(
              successHandler -> {
                if (providerOwnerCache.getIfPresent(id) != null) {
                  promise.complete(providerOwnerCache.getIfPresent(id));
                } else {
                  LOGGER.info("id :{} not found in catalogue server", id);
                    promise.fail(new DxBadRequestException(BAD_REQUEST_ERROR));
                }
              })
          .onFailure(promise::fail);
    }
    return promise.future();
  }

  public Future<Void> refreshCatalogue() {
    LOGGER.trace("refresh catalogue() called");
    Promise<Void> promise = Promise.promise();
    catalogueClient
        .fetchCatalogueData()
        .onSuccess(
            successHandler -> {
                if(successHandler==null ){
                    promise.complete();
                }
                else {
                    catalogueCache.invalidateAll();
                    successHandler.forEach(
                            result -> {
                                JsonObject res = (JsonObject) result;
                                String rsId = res.getString("id");
                                catalogueCache.put(rsId, res);
                            });
                    promise.complete();
                }
            })
        .onFailure(
            failure -> {
              LOGGER.error("Failed to refresh catalogue", failure);
                promise.fail(failure);
            });
    return promise.future();
  }

  public Future<Void> idCatalogueInfo(String id) {
    LOGGER.trace("id ::{}", id);
    Promise<Void> promise = Promise.promise();
    catalogueClient
        .getCatalogueInfoForId(id)
        .onSuccess(
            successHandler -> {
              if (successHandler.isEmpty()) {
                LOGGER.error("id :{} not found in catalogue server", id);
                  promise.fail(new DxBadRequestException(BAD_REQUEST_ERROR));
              } else {
                successHandler
                    .get()
                    .forEach(
                        result -> {
                          JsonObject res = (JsonObject) result;
                          catalogueCache.put(id, res);
                        });
                promise.complete();
              }
            })
        .onFailure(
            failure -> {
              LOGGER.error("Failed to found id catalogue");
                promise.fail(failure);
            });
    return promise.future();
  }

  private Future<Void> providerOwnerInfo(String id) {
    LOGGER.trace("id to check provider info:: {}", id);
    Promise<Void> promise = Promise.promise();
    catalogueClient
        .getProviderOwnerUserId(id)
        .onSuccess(
            successHandler -> {
              providerOwnerCache.put(id, successHandler);
              promise.complete();
            })
        .onFailure(
            failure -> {
              LOGGER.error("Failed to provider id details in catalogue {}", id);

              promise.fail(
                 failure);
            });

    return promise.future();
  }
}
