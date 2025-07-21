package org.cdpg.dx.tgdex.validator.service;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * The Validator Service.
 * <h1>Validator Service</h1>
 * <p>
 * The Validator Service in the IUDX Catalogue Server defines the operations to be performed with
 * the IUDX File server.
 * </p>
 *
 *
 * @see ProxyGen
 *
 * @see VertxGen
 * @version 1.0
 * @since 2020-05-31
 */

@VertxGen
@ProxyGen
public interface ValidatorService {

  /**
   * The createProxy helps the code generation blocks to generate proxy code.
   *
   * @param vertx which is the vertx instance
   * @param address which is the proxy address
   * @return ValidatorServiceVertxEBProxy which is a service proxy
   */

  @GenIgnore
  static ValidatorService createProxy(Vertx vertx, String address) {
    return new ValidatorServiceVertxEBProxy(vertx, address);
  }
  Future<Void> validateSchema(JsonObject request);// CRUD api me

  Future<Void> validateItem(JsonObject request); // CRUD api me

  Future<Void> validateSearchQuery(JsonObject requestData);

}