package iudx.resource.server.databroker;

import static iudx.resource.server.databroker.util.Constants.*;
import static iudx.resource.server.databroker.util.Util.*;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import iudx.resource.server.databroker.util.Constants;
import iudx.resource.server.databroker.util.Util;

public class RabbitClient {

  private static final Logger LOGGER = LogManager.getLogger(RabbitClient.class);

  private RabbitMQClient client;
  private RabbitWebClient webClient;
  private PostgresClient pgSQLClient;

  public RabbitClient(Vertx vertx, RabbitMQOptions rabbitConfigs, RabbitWebClient webClient,
      PostgresClient pgSQLClient) {
    this.client = getRabbitMQClient(vertx, rabbitConfigs);
    this.webClient = webClient;
    this.pgSQLClient = pgSQLClient;
    client.start(clientStartupHandler -> {
      if (clientStartupHandler.succeeded()) {
        LOGGER.debug("Info : rabbit MQ client started");
      } else if (clientStartupHandler.failed()) {
        LOGGER.fatal("Fail : rabbit MQ client startup failed.");
      }
    });
  }

  private RabbitMQClient getRabbitMQClient(Vertx vertx, RabbitMQOptions rabbitConfigs) {
    return RabbitMQClient.create(vertx, rabbitConfigs);
  }

  /**
   * The createExchange implements the create exchange.
   * 
   * @param request which is a Json object
   * @Param vHost virtual-host
   * @return response which is a Future object of promise of Json type
   **/
  public Future<JsonObject> createExchange(JsonObject request, String vHost) {
    LOGGER.debug("Info : RabbitClient#createExchage() started");
    Promise<JsonObject> promise = Promise.promise();
    if (request != null && !request.isEmpty()) {
      String exchangeName = request.getString("exchangeName");
      String url = "/api/exchanges/" + vHost + "/" + encodeValue(exchangeName);
      JsonObject obj = new JsonObject();
      obj.put(TYPE, EXCHANGE_TYPE);
      obj.put(AUTO_DELETE, false);
      obj.put(DURABLE, true);
      webClient.requestAsync(REQUEST_PUT, url, obj).onComplete(requestHandler -> {
        if (requestHandler.succeeded()) {
          JsonObject responseJson = new JsonObject();
          HttpResponse<Buffer> response = requestHandler.result();
          int statusCode = response.statusCode();
          //System.out.println(statusCode);
          if (statusCode == HttpStatus.SC_CREATED) {
            responseJson.put(EXCHANGE, exchangeName);
          } else if (statusCode == HttpStatus.SC_NO_CONTENT) {
            responseJson = Util.getResponseJson(HttpStatus.SC_CONFLICT, FAILURE, EXCHANGE_EXISTS);
          } else if (statusCode == HttpStatus.SC_BAD_REQUEST) {
            responseJson = Util.getResponseJson(statusCode, FAILURE,
                EXCHANGE_EXISTS_WITH_DIFFERENT_PROPERTIES);
          }
          LOGGER.debug("Success : " + responseJson);
          promise.complete(responseJson);
        } else {
          JsonObject errorJson = Util.getResponseJson(HttpStatus.SC_INTERNAL_SERVER_ERROR, ERROR,
              EXCHANGE_CREATE_ERROR);
          LOGGER.error("Fail : " + requestHandler.cause());
          promise.fail(errorJson.toString());
        }
      });
    }
    return promise.future();
  }

  Future<JsonObject> getExchangeDetails(JsonObject request, String vHost) {
    LOGGER.debug("Info : RabbitClient#getExchange() started");
    Promise<JsonObject> promise = Promise.promise();
    if (request != null && !request.isEmpty()) {
      String exchangeName = request.getString("exchangeName");
      String url = "/api/exchanges/" + vHost + "/" + encodeValue(exchangeName);
      webClient.requestAsync(REQUEST_GET, url).onComplete(requestHandler -> {
        if (requestHandler.succeeded()) {
          JsonObject responseJson = new JsonObject();
          HttpResponse<Buffer> response = requestHandler.result();
          int statusCode = response.statusCode();
          if (statusCode == HttpStatus.SC_OK) {
            responseJson = new JsonObject(response.body().toString());
          } else {
            responseJson = Util.getResponseJson(statusCode, FAILURE, EXCHANGE_NOT_FOUND);
          }
          LOGGER.debug("Success : " + responseJson);
          promise.complete(responseJson);
        } else {
          JsonObject errorJson =
              Util.getResponseJson(HttpStatus.SC_INTERNAL_SERVER_ERROR, ERROR, EXCHANGE_NOT_FOUND);
          LOGGER.error("Error : " + requestHandler.cause());
          promise.fail(errorJson.toString());
        }
      });
    }
    return promise.future();
  }

  Future<JsonObject> getExchange(JsonObject request, String vhost) {
    JsonObject response = new JsonObject();
    Promise<JsonObject> promise = Promise.promise();
    if (request != null && !request.isEmpty()) {
      String exchangeName = request.getString("id");
      String url;
      url = "/api/exchanges/" + vhost + "/" + encodeValue(exchangeName);
      webClient.requestAsync(REQUEST_GET, url).onComplete(result -> {
        if (result.succeeded()) {
          int status = result.result().statusCode();
          response.put(TYPE, status);
          if (status == HttpStatus.SC_OK) {
            response.put(TITLE, SUCCESS);
            response.put(DETAIL, EXCHANGE_FOUND);
          } else if (status == HttpStatus.SC_NOT_FOUND) {
            response.put(TITLE, FAILURE);
            response.put(DETAIL, EXCHANGE_NOT_FOUND);
          } else {
            response.put("getExchange_status", status);
            promise.fail("getExchange_status" + result.cause());
          }
        } else {
          response.put("getExchange_error", result.cause());
          promise.fail("getExchange_error" + result.cause());
        }
        LOGGER.info("getExchange method response : " + response);
        promise.complete(response);
      });

    } else {
      promise.fail("exchangeName not provided");
    }
    return promise.future();

  }

  /**
   * The deleteExchange implements the delete exchange operation.
   * 
   * @param request which is a Json object
   * @Param VHost virtual-host
   * @return response which is a Future object of promise of Json type
   */
  Future<JsonObject> deleteExchange(JsonObject request, String vHost) {
    LOGGER.debug("Info : RabbitClient#deleteExchange() started");
    Promise<JsonObject> promise = Promise.promise();
    if (request != null && !request.isEmpty()) {
      String exchangeName = request.getString("exchangeName");
      String url = "/api/exchanges/" + vHost + "/" + encodeValue(exchangeName);
      webClient.requestAsync(REQUEST_DELETE, url).onComplete(requestHandler -> {
        if (requestHandler.succeeded()) {
          JsonObject responseJson = new JsonObject();
          HttpResponse<Buffer> response = requestHandler.result();
          int statusCode = response.statusCode();
          if (statusCode == HttpStatus.SC_NO_CONTENT) {
            responseJson = new JsonObject();
            responseJson.put(EXCHANGE, exchangeName);
          } else {
            responseJson = Util.getResponseJson(statusCode, FAILURE, EXCHANGE_NOT_FOUND);
            LOGGER.debug("Success : " + responseJson);
          }
          promise.complete(responseJson);
        } else {
          JsonObject errorJson = Util.getResponseJson(HttpStatus.SC_INTERNAL_SERVER_ERROR, ERROR,
              EXCHANGE_DELETE_ERROR);
          LOGGER.error("Error : " + requestHandler.cause());
          promise.fail(errorJson.toString());
        }
      });
    }
    return promise.future();
  }

  /**
   * The listExchangeSubscribers implements the list of bindings for an exchange (source).
   * 
   * @param request which is a Json object
   * @param vHost virtual-host
   * @return response which is a Future object of promise of Json type
   */
  Future<JsonObject> listExchangeSubscribers(JsonObject request, String vhost) {
    LOGGER.debug("Info : RabbitClient#listExchangeSubscribers() started");
    Promise<JsonObject> promise = Promise.promise();
    JsonObject finalResponse = new JsonObject();
    if (request != null && !request.isEmpty()) {
      String exchangeName = request.getString(ID);
      String url =
          "/api/exchanges/" + vhost + "/" + Util.encodeValue(exchangeName) + "/bindings/source";
      webClient.requestAsync(REQUEST_GET, url).onComplete(ar -> {
        if (ar.succeeded()) {
          HttpResponse<Buffer> response = ar.result();
          if (response != null && !response.equals(" ")) {
            int status = response.statusCode();
            if (status == HttpStatus.SC_OK) {
              Buffer body = response.body();
              if (body != null) {
                JsonArray jsonBody = new JsonArray(body.toString());
                Map res = jsonBody.stream().map(JsonObject.class::cast)
                    .collect(Collectors.toMap(json -> json.getString("destination"),
                        json -> new JsonArray().add(json.getString("routing_key")),
                        Util.bindingMergeOperator));
                LOGGER.debug("Info : exchange subscribers : " + jsonBody);
                finalResponse.clear().mergeIn(new JsonObject(res));
                LOGGER.debug("Info : final Response : " + finalResponse);
                if (finalResponse.isEmpty()) {
                  finalResponse.clear().mergeIn(
                      Util.getResponseJson(HttpStatus.SC_NOT_FOUND, FAILURE, EXCHANGE_NOT_FOUND),
                      true);
                }
              }
            } else if (status == HttpStatus.SC_NOT_FOUND) {
              finalResponse.mergeIn(
                  Util.getResponseJson(HttpStatus.SC_NOT_FOUND, FAILURE, EXCHANGE_NOT_FOUND), true);
            }
          }
          promise.complete(finalResponse);
          LOGGER.debug("Success :" + finalResponse);
        } else {
          LOGGER.error("Fail : Listing of Exchange failed - " + ar.cause());
          JsonObject error = Util.getResponseJson(500, FAILURE, "Internal server error");
          promise.fail(error.toString());
        }
      });
    }
    return promise.future();
  }

  /**
   * The createQueue implements the create queue operation.
   * 
   * @param request which is a Json object
   * @param vHost virtual-host
   * @return response which is a Future object of promise of Json type
   */
  Future<JsonObject> createQueue(JsonObject request, String vhost) {
    LOGGER.debug("Info : RabbitClient#createQueue() started");
    Promise<JsonObject> promise = Promise.promise();
    JsonObject finalResponse = new JsonObject();
    if (request != null && !request.isEmpty()) {
      String queueName = request.getString("queueName");
      String url = "/api/queues/" + vhost + "/" + Util.encodeValue(queueName);//"durable":true
      JsonObject configProp = new JsonObject();
      JsonObject arguments = new JsonObject();
      arguments.put(Constants.X_MESSAGE_TTL_NAME, Constants.X_MESSAGE_TTL_VALUE)
          .put(Constants.X_MAXLENGTH_NAME, Constants.X_MAXLENGTH_VALUE)
          .put(Constants.X_QUEUE_MODE_NAME, Constants.X_QUEUE_MODE_VALUE);
      configProp.put(Constants.X_QUEUE_TYPE,true);
      configProp.put(Constants.X_QUEUE_ARGUMENTS, arguments);
      webClient.requestAsync(REQUEST_PUT, url, configProp).onComplete(ar -> {
        if (ar.succeeded()) {
          HttpResponse<Buffer> response = ar.result();
          if (response != null && !response.equals(" ")) {
            int status = response.statusCode();
            if (status == HttpStatus.SC_CREATED) {
              finalResponse.put(Constants.QUEUE, queueName);
            } else if (status == HttpStatus.SC_NO_CONTENT) {
              finalResponse.mergeIn(
                  Util.getResponseJson(HttpStatus.SC_CONFLICT, FAILURE, QUEUE_ALREADY_EXISTS),
                  true);
            } else if (status == HttpStatus.SC_BAD_REQUEST) {
              finalResponse.mergeIn(Util.getResponseJson(status, FAILURE,
                  QUEUE_ALREADY_EXISTS_WITH_DIFFERENT_PROPERTIES), true);
            }
          }
          promise.complete(finalResponse);
          LOGGER.info("Success : " + finalResponse);
        } else {
          LOGGER.error("Fail : Creation of Queue failed - " + ar.cause());
          finalResponse.mergeIn(Util.getResponseJson(500, FAILURE, QUEUE_CREATE_ERROR));
          promise.fail(finalResponse.toString());
        }
      });
    }
    return promise.future();
  }


  /**
   * The deleteQueue implements the delete queue operation.
   * 
   * @param request which is a Json object
   * @param vhost virtual-host
   * @return response which is a Future object of promise of Json type
   */
  Future<JsonObject> deleteQueue(JsonObject request, String vhost) {
    LOGGER.debug("Info : RabbitClient#deleteQueue() started");
    Promise<JsonObject> promise = Promise.promise();
    JsonObject finalResponse = new JsonObject();
    if (request != null && !request.isEmpty()) {
      String queueName = request.getString("queueName");
      LOGGER.debug("Info : queuName" + queueName);
      String url = "/api/queues/" + vhost + "/" + encodeValue(queueName);
      webClient.requestAsync(REQUEST_DELETE, url).onComplete(ar -> {
        if (ar.succeeded()) {
          HttpResponse<Buffer> response = ar.result();
          if (response != null && !response.equals(" ")) {
            int status = response.statusCode();
            if (status == HttpStatus.SC_NO_CONTENT) {
              finalResponse.put(Constants.QUEUE, queueName);
            } else if (status == HttpStatus.SC_NOT_FOUND) {
              finalResponse.mergeIn(Util.getResponseJson(status, FAILURE, QUEUE_DOES_NOT_EXISTS));
            }
          }
          LOGGER.info(finalResponse);
          promise.complete(finalResponse);
        } else {
          LOGGER.error("Fail : deletion of queue failed - " + ar.cause());
          finalResponse.mergeIn(Util.getResponseJson(500, FAILURE, QUEUE_DELETE_ERROR));
          promise.fail(finalResponse.toString());
        }
      });
    }
    return promise.future();
  }

  /**
   * The bindQueue implements the bind queue to exchange by routing key.
   * 
   * @param request which is a Json object
   * @param vhost virtual-host
   * @return response which is a Future object of promise of Json type
   */
  Future<JsonObject> bindQueue(JsonObject request, String vhost) {
    LOGGER.debug("Info : RabbitClient#bindQueue() started");
    JsonObject finalResponse = new JsonObject();
    JsonObject requestBody = new JsonObject();
    Promise<JsonObject> promise = Promise.promise();
    if (request != null && !request.isEmpty()) {
      String exchangeName = request.getString("exchangeName");
      String queueName = request.getString("queueName");
      JsonArray entities = request.getJsonArray("entities");
      int arrayPos = entities.size() - 1;
      String url = "/api/bindings/" + vhost + "/e/" + encodeValue(exchangeName) + "/q/"
          + Util.encodeValue(queueName);
      for (Object rkey : entities) {
        requestBody.put("routing_key", rkey.toString());
        webClient.requestAsync(REQUEST_POST, url, requestBody).onComplete(ar -> {
          if (ar.succeeded()) {
            HttpResponse<Buffer> response = ar.result();
            if (response != null && !response.equals(" ")) {
              int status = response.statusCode();
              LOGGER.info("Info : Binding " + rkey.toString() + "Success. Status is " + status);
              if (status == HttpStatus.SC_CREATED) {
                finalResponse.put(Constants.EXCHANGE, exchangeName);
                finalResponse.put(Constants.QUEUE, queueName);
                finalResponse.put(Constants.ENTITIES, entities);
              } else if (status == HttpStatus.SC_NOT_FOUND) {
                finalResponse
                    .mergeIn(Util.getResponseJson(status, FAILURE, QUEUE_EXCHANGE_NOT_FOUND));
              }
            }
            if (rkey == entities.getValue(arrayPos)) {
              LOGGER.debug("Success : " + finalResponse);
              promise.complete(finalResponse);
            }
          } else {
            LOGGER.error("Fail : Binding of Queue failed - " + ar.cause());
            finalResponse.mergeIn(Util.getResponseJson(500, FAILURE, QUEUE_BIND_ERROR));
            promise.fail(finalResponse.toString());
          }
        });
      }
    }
    return promise.future();
  }

  /**
   * The unbindQueue implements the unbind queue to exchange by routing key.
   * 
   * @param request which is a Json object
   * @param vhost virtual-host
   * @return response which is a Future object of promise of Json type
   */
  Future<JsonObject> unbindQueue(JsonObject request, String vhost) {
    LOGGER.debug("Info : RabbitClient#unbindQueue() started");
    JsonObject finalResponse = new JsonObject();
    Promise<JsonObject> promise = Promise.promise();
    if (request != null && !request.isEmpty()) {
      String exchangeName = request.getString("exchangeName");
      String queueName = request.getString("queueName");
      JsonArray entities = request.getJsonArray("entities");
      int arrayPos = entities.size() - 1;
      for (Object rkey : entities) {
        String url = "/api/bindings/" + vhost + "/e/" + encodeValue(exchangeName) + "/q/"
            + Util.encodeValue(queueName) + "/" + encodeValue((String) rkey);
        webClient.requestAsync(REQUEST_DELETE, url).onComplete(ar -> {
          if (ar.succeeded()) {
            HttpResponse<Buffer> response = ar.result();
            if (response != null && !response.equals(" ")) {
              int status = response.statusCode();
              if (status == HttpStatus.SC_NO_CONTENT) {
                finalResponse.put(Constants.EXCHANGE, exchangeName);
                finalResponse.put(Constants.QUEUE, queueName);
                finalResponse.put(Constants.ENTITIES, entities);
              } else if (status == HttpStatus.SC_NOT_FOUND) {
                finalResponse.mergeIn(Util.getResponseJson(status, FAILURE, ALL_NOT_FOUND));
              }
            }
            if (rkey == entities.getValue(arrayPos)) {
              LOGGER.debug("Success : " + finalResponse);
              promise.complete(finalResponse);
            }
          } else {
            LOGGER.error("Fail : Unbinding of Queue failed" + ar.cause());
            finalResponse.mergeIn(Util.getResponseJson(500, FAILURE, QUEUE_BIND_ERROR));
            promise.fail(finalResponse.toString());
          }
        });
      }
    }
    return promise.future();
  }

  /**
   * The createvHost implements the create virtual host operation.
   * 
   * @param request which is a Json object
   * @return response which is a Future object of promise of Json type
   */
  Future<JsonObject> createvHost(JsonObject request) {
    LOGGER.debug("Info : RabbitClient#createvHost() started");
    JsonObject finalResponse = new JsonObject();
    Promise<JsonObject> promise = Promise.promise();
    if (request != null && !request.isEmpty()) {
      String vhost = request.getString("vHost");
      String url = "/api/vhosts/" + encodeValue(vhost);
      webClient.requestAsync(REQUEST_PUT, url).onComplete(ar -> {
        if (ar.succeeded()) {
          HttpResponse<Buffer> response = ar.result();
          if (response != null && !response.equals(" ")) {
            int status = response.statusCode();
            if (status == HttpStatus.SC_CREATED) {
              finalResponse.put(Constants.VHOST, vhost);
            } else if (status == HttpStatus.SC_NO_CONTENT) {
              finalResponse.mergeIn(
                  Util.getResponseJson(HttpStatus.SC_CONFLICT, FAILURE, VHOST_ALREADY_EXISTS));
            }
          }
          promise.complete(finalResponse);
          LOGGER.info("Success : " + finalResponse);
        } else {
          LOGGER.error(" Fail : Creation of vHost failed" + ar.cause());
          finalResponse.mergeIn(Util.getResponseJson(500, FAILURE, VHOST_CREATE_ERROR));
          promise.fail(finalResponse.toString());
        }
      });
    }
    return promise.future();
  }

  /**
   * The deletevHost implements the delete virtual host operation.
   * 
   * @param request which is a Json object
   * @return response which is a Future object of promise of Json type
   */
  Future<JsonObject> deletevHost(JsonObject request) {
    LOGGER.debug("Info : RabbitClient#deletevHost() started");
    JsonObject finalResponse = new JsonObject();
    Promise<JsonObject> promise = Promise.promise();
    if (request != null && !request.isEmpty()) {
      String vhost = request.getString("vHost");
      String url = "/api/vhosts/" + encodeValue(vhost);
      webClient.requestAsync(REQUEST_DELETE, url).onComplete(ar -> {
        if (ar.succeeded()) {
          HttpResponse<Buffer> response = ar.result();
          if (response != null && !response.equals(" ")) {
            int status = response.statusCode();
            LOGGER.debug("Info : statusCode" + status);
            if (status == HttpStatus.SC_NO_CONTENT) {
              finalResponse.put(Constants.VHOST, vhost);
            } else if (status == HttpStatus.SC_NOT_FOUND) {
              finalResponse.mergeIn(Util.getResponseJson(status, FAILURE, VHOST_NOT_FOUND));
            }
          }
          promise.complete(finalResponse);
          LOGGER.info("Success : " + finalResponse);
        } else {
          LOGGER.error("Fail : Deletion of vHost failed -" + ar.cause());
          finalResponse.mergeIn(Util.getResponseJson(500, FAILURE, VHOST_DELETE_ERROR));
          promise.fail(finalResponse.toString());
        }
      });
    }

    return promise.future();
  }

  /**
   * The listvHost implements the list of virtual hosts .
   * 
   * @param request which is a Json object
   * @return response which is a Future object of promise of Json type
   */
  Future<JsonObject> listvHost(JsonObject request) {
    LOGGER.debug("Info : RabbitClient#listvHost() started");
    JsonObject finalResponse = new JsonObject();
    Promise<JsonObject> promise = Promise.promise();
    if (request != null) {
      JsonArray vhostList = new JsonArray();
      String url = "/api/vhosts";
      webClient.requestAsync(REQUEST_GET, url).onComplete(ar -> {
        if (ar.succeeded()) {
          HttpResponse<Buffer> response = ar.result();
          if (response != null && !response.equals(" ")) {
            int status = response.statusCode();
            LOGGER.debug("Info : statusCode" + status);
            if (status == HttpStatus.SC_OK) {
              Buffer body = response.body();
              if (body != null) {
                JsonArray jsonBody = new JsonArray(body.toString());
                jsonBody.forEach(current -> {
                  JsonObject currentJson = new JsonObject(current.toString());
                  String vhostName = currentJson.getString("name");
                  vhostList.add(vhostName);
                });
                if (vhostList != null && !vhostList.isEmpty()) {
                  finalResponse.put(Constants.VHOST, vhostList);
                }
              }
            } else if (status == HttpStatus.SC_NOT_FOUND) {
              finalResponse.mergeIn(Util.getResponseJson(status, FAILURE, VHOST_NOT_FOUND));
            }
          }
          LOGGER.info("Success : " + finalResponse);
          promise.complete(finalResponse);
        } else {
          LOGGER.error("Fail : Listing of vHost failed - " + ar.cause());
          finalResponse.mergeIn(Util.getResponseJson(500, FAILURE, VHOST_LIST_ERROR));
          promise.fail(finalResponse.toString());
        }
      });
    }
    return promise.future();
  }

  /**
   * The listQueueSubscribers implements the list of bindings for a queue.
   * 
   * @param request which is a Json object
   * @return response which is a Future object of promise of Json type
   */
  Future<JsonObject> listQueueSubscribers(JsonObject request, String vhost) {
    LOGGER.debug("Info : RabbitClient#listQueueSubscribers() started");
    JsonObject finalResponse = new JsonObject();
    Promise<JsonObject> promise = Promise.promise();
    if (request != null && !request.isEmpty()) {
      String queueName = request.getString("queueName");
      JsonArray oroutingKeys = new JsonArray();
      String url = "/api/queues/" + vhost + "/" + encodeValue(queueName) + "/bindings";
      webClient.requestAsync(REQUEST_GET, url).onComplete(ar -> {
        if (ar.succeeded()) {
          HttpResponse<Buffer> response = ar.result();
          if (response != null && !response.equals(" ")) {
            int status = response.statusCode();
            LOGGER.debug("Info : statusCode " + status);
            if (status == HttpStatus.SC_OK) {
              Buffer body = response.body();
              if (body != null) {
                JsonArray jsonBody = new JsonArray(body.toString());
                jsonBody.forEach(current -> {
                  JsonObject currentJson = new JsonObject(current.toString());
                  String rkeys = currentJson.getString("routing_key");
                  if (rkeys != null && !rkeys.equalsIgnoreCase(queueName)) {
                    oroutingKeys.add(rkeys);
                  }
                });
                if (oroutingKeys != null && !oroutingKeys.isEmpty()) {
                  finalResponse.put(Constants.ENTITIES, oroutingKeys);
                } else {
                  finalResponse.clear().mergeIn(Util.getResponseJson(HttpStatus.SC_NOT_FOUND,
                      FAILURE, QUEUE_DOES_NOT_EXISTS));
                }
              }
            } else if (status == HttpStatus.SC_NOT_FOUND) {
              finalResponse.clear()
                  .mergeIn(Util.getResponseJson(status, FAILURE, QUEUE_DOES_NOT_EXISTS));
            }
          }
          LOGGER.debug("Info : " + finalResponse);
          promise.complete(finalResponse);
        } else {
          LOGGER.error("Error : Listing of Queue failed - " + ar.cause());
          finalResponse.mergeIn(Util.getResponseJson(500, FAILURE, QUEUE_LIST_ERROR));
          promise.fail(finalResponse.toString());
        }
      });
    }
    return promise.future();
  }

  public Future<JsonObject> registerAdapter(JsonObject request, String vhost) {
    LOGGER.debug("Info : RabbitClient#registerAdaptor() started");
    Promise<JsonObject> promise = Promise.promise();

    AdaptorResultContainer requestParams = new AdaptorResultContainer();
    requestParams.vhost = vhost;
    requestParams.id = request.getString("resourceGroup");
    requestParams.resourceServer = request.getString("resourceServer");
    requestParams.userName = request.getString(CONSUMER);
    requestParams.provider = request.getString("provider");
    requestParams.domain = requestParams.userName.substring(requestParams.userName.indexOf("@") + 1,
        requestParams.userName.length());
    requestParams.userNameSha = getSha(requestParams.userName);
    requestParams.userId = requestParams.domain + "/" + requestParams.userNameSha;
    requestParams.adaptorId =
        requestParams.provider + "/" + requestParams.resourceServer + "/" + requestParams.id;

    if (isValidId.test(requestParams.id)) {
      if (requestParams.id != null && !requestParams.id.isEmpty() && !requestParams.id.isBlank()) {
        Future<JsonObject> userCreationFuture = createUserIfNotExist(requestParams.userName, vhost);
        userCreationFuture.compose(userCreationResult -> {
          requestParams.apiKey = userCreationResult.getString("apiKey");
          JsonObject json = new JsonObject();
          json.put(EXCHANGE_NAME, requestParams.adaptorId);
          LOGGER.debug("Success : User created/exist.");
          return createExchange(json, vhost);
        }).compose(createExchangeResult -> {
          if (createExchangeResult.containsKey("detail")) {
            LOGGER.error("Error : Exchange creation failed. ");
            return Future.failedFuture(createExchangeResult.toString());
          }
          LOGGER.debug("Success : Exchange created successfully.");
          return setTopicPermissions(requestParams.vhost, requestParams.adaptorId,
              requestParams.userId);
        }).compose(topicPermissionsResult -> {
          LOGGER.debug("Success : topic permissions set.");
          return queueBinding(requestParams.adaptorId);
        }).onSuccess(success -> {
          LOGGER.debug("Success : queue bindings done.");
          JsonObject response = new JsonObject()
              .put(USER_NAME, requestParams.userId)
              .put(Constants.APIKEY, requestParams.apiKey)
              .put(Constants.ID, requestParams.adaptorId)
              .put(Constants.URL, BROKER_PRODUCTION_DOMAIN)
              .put(Constants.PORT, BROKER_PRODUCTION_PORT)
              .put(Constants.VHOST, VHOST_IUDX);
          LOGGER.debug("Success : Adapter created successfully.");
          promise.complete(response);
        }).onFailure(failure -> {
          LOGGER.info("Error : " + failure);
          promise.fail(failure);
        });
      } else {
        promise.fail(
            getResponseJson(BAD_REQUEST_CODE, BAD_REQUEST_DATA, "Invalid/Missing Parameters")
                .toString());
      }
    } else {
      promise
          .fail(getResponseJson(BAD_REQUEST_CODE, BAD_REQUEST_DATA, "Invalid/Missing Parameters")
              .toString());
    }
    return promise.future();
  }

  private class AdaptorResultContainer {
    public String apiKey;
    public String id;
    public String resourceServer;
    public String userName;
    public String userNameSha;
    public String provider;
    public String domain;
    public String userId;
    public String adaptorId;
    public String vhost;

  }

  @Deprecated
  public Future<JsonObject> registerAdaptor_V1(JsonObject request, String vhost) {
    LOGGER.debug("Info : RabbitClient#registerAdaptor() started");
    Promise<JsonObject> promise = Promise.promise();
    //System.out.println(request.toString());
    /* Get the ID and userName from the request */
    String id = request.getString("resourceGroup");
    String resourceServer = request.getString("resourceServer");
    String userName = request.getString(CONSUMER);

    String provider = request.getString("provider");
    LOGGER.debug("Info : Resource Group Name given by user is : " + id);
    LOGGER.debug("Info : Resource Server Name by user is : " + resourceServer);
    LOGGER.debug("Info : User Name is : " + userName);
    /* Construct a response object */
    JsonObject registerResponse = new JsonObject();
    /* Validate the request object */
    if (request != null && !request.isEmpty()) {
      /* Goto Create user if ID is not empty */
      if (id != null && !id.isEmpty() && !id.isBlank()) {
        /* Validate the ID for special characters */
        if (Util.isValidId.test(id)) {
          /* Validate the userName */
          if (userName != null && !userName.isBlank() && !userName.isEmpty()) {
            /* Create a new user, if it does not exists */
            Future<JsonObject> userCreationFuture = createUserIfNotExist(userName, vhost);
            /* On completion of user creation, handle the result */
            userCreationFuture.onComplete(rh -> {
              if (rh.succeeded()) {
                /* Obtain the result of user creation */
                JsonObject result = rh.result();
                LOGGER.debug("Info : Response of createUserIfNotExist is : " + result);
                /* Construct the domain, userNameSHA, userID and adaptorID */
                String domain = userName.substring(userName.indexOf("@") + 1, userName.length());
                String userNameSha = Util.getSha(userName);
                String userID = domain + "/" + userNameSha;
                String adaptorID = provider + "/" + resourceServer + "/" + id;
                String apikey = result.getString(APIKEY);
                LOGGER.debug("Info : userID is : " + userID);
                LOGGER.debug("Info : adaptorID is : " + adaptorID);
                LOGGER.debug("Info : apikey is : " + apikey);
                if (adaptorID != null && !adaptorID.isBlank() && !adaptorID.isEmpty()) {
                  JsonObject json = new JsonObject();
                  json.put(EXCHANGE_NAME, adaptorID);
                  /* Create an exchange if it does not exists */
                  Future<JsonObject> exchangeDeclareFuture = createExchange(json, vhost);
                  /* On completion of exchange creation, handle the result */
                  exchangeDeclareFuture.onComplete(ar -> {
                    if (ar.succeeded()) {
                      /* Obtain the result of exchange creation */
                      JsonObject obj = ar.result();
                      LOGGER.debug("Info : Response of createExchange is : " + obj);
                      LOGGER.debug("Info : exchange name provided : " + adaptorID);
                      LOGGER.debug("Info : exchange name received : " + obj.getString("exchange"));
                      // if exchange just registered then set topic permission and bind with queues
                      if (!obj.containsKey("detail")) {
                        Future<JsonObject> topicPermissionFuture =
                            setTopicPermissions(vhost, adaptorID, userID);
                        topicPermissionFuture.onComplete(topicHandler -> {
                          if (topicHandler.succeeded()) {
                            LOGGER.debug("Success : Write permission set on topic for exchange "
                                + obj.getString("exchange"));
                            /* Bind the exchange with the database and adaptorLogs queue */
                            Future<JsonObject> queueBindFuture = queueBinding(adaptorID);
                            queueBindFuture.onComplete(res -> {
                              if (res.succeeded()) {
                                LOGGER.debug(
                                    "Success : Queue_Database, Queue_adaptorLogs binding done with "
                                        + obj.getString("exchange") + " exchange");
                                /* Construct the response for registration of adaptor */
                                registerResponse.put(USER_NAME, userID);
                                /*
                                 * APIKEY should be equal to password generated. For testing use
                                 * APIKEY_TEST_EXAMPLE
                                 */
                                registerResponse.put(Constants.APIKEY, apikey);
                                registerResponse.put(Constants.ID, adaptorID);
                                registerResponse.put(Constants.URL,
                                    Constants.BROKER_PRODUCTION_DOMAIN);
                                registerResponse.put(Constants.PORT,
                                    Constants.BROKER_PRODUCTION_PORT);
                                registerResponse.put(Constants.VHOST, Constants.VHOST_IUDX);

                                LOGGER.debug("Info : registerResponse : " + registerResponse);
                                promise.complete(registerResponse);
                              } else {
                                /* Handle Queue Error */
                                LOGGER.error(
                                    "Error : error in queue binding with adaptor - " + res.cause());
                                registerResponse.clear().mergeIn(
                                    getResponseJson(BAD_REQUEST_CODE, ERROR, QUEUE_BIND_ERROR));
                                promise.fail(registerResponse.toString());
                              }
                            });
                          } else {
                            /* Handle Topic Permission Error */
                            LOGGER.error("Error : topic permissions not set for exchange "
                                + obj.getString("exchange") + " - cause : "
                                + topicHandler.cause().getMessage());
                            registerResponse.clear().mergeIn(getResponseJson(BAD_REQUEST_CODE,
                                ERROR, TOPIC_PERMISSION_SET_ERROR));
                            promise.fail(registerResponse.toString());
                          }
                        });
                      } else if (obj.getString("detail") != null
                          && !obj.getString("detail").isEmpty()
                          && obj.getString("detail").equalsIgnoreCase("Exchange already exists")) {
                        /* Handle Exchange Error */
                        LOGGER.error(
                            "Error : something wrong in exchange declaration : " + ar.cause());
                        registerResponse.clear()
                            .mergeIn(getResponseJson(BAD_REQUEST_CODE, ERROR, EXCHANGE_EXISTS));
                        promise.fail(registerResponse.toString());
                      }
                    } else {
                      /* Handle Exchange Error */
                      registerResponse.clear().mergeIn(
                          getResponseJson(BAD_REQUEST_CODE, ERROR, EXCHANGE_DECLARATION_ERROR));
                      promise.fail(registerResponse.toString());
                    }
                  });
                } else {
                  /* Handle Request Error */
                  LOGGER.error("Error : AdaptorID / Exchange not provided in request");
                  registerResponse.clear()
                      .mergeIn(getResponseJson(BAD_REQUEST_CODE, ERROR, ADAPTER_ID_NOT_PROVIDED));
                  promise.fail(registerResponse.toString());
                }
              } else if (rh.failed()) {
                /* Handle User Creation Error */
                LOGGER.error("Error : User creation failed. " + rh.cause());
                registerResponse.clear()
                    .mergeIn(getResponseJson(BAD_REQUEST_CODE, ERROR, USER_CREATION_ERROR));
                promise.fail(registerResponse.toString());
              } else {
                /* Handle User Creation Error */
                LOGGER.error("Error : User creation failed. " + rh.cause());
                registerResponse.clear()
                    .mergeIn(getResponseJson(BAD_REQUEST_CODE, ERROR, USER_CREATION_ERROR));
                promise.fail(registerResponse.toString());
              }
            });
          } else {
            /* Handle Request Error */
            LOGGER.error("Error : user not provided in adaptor registration");
            registerResponse.clear()
                .mergeIn(getResponseJson(BAD_REQUEST_CODE, ERROR, USER_NAME_NOT_PROVIDED));
            promise.fail(registerResponse.toString());
          }
        } else {
          /* Handle Invalid ID Error */
          registerResponse.clear().mergeIn(getResponseJson(BAD_REQUEST_CODE, ERROR, INVALID_ID));
          promise.fail(registerResponse.toString());
          LOGGER.error("Error : id not provided in adaptor registration");
        }
      } else {
        /* Handle Request Error */
        LOGGER.error("Error : id not provided in adaptor registration");
        registerResponse.clear().mergeIn(getResponseJson(BAD_REQUEST_CODE, ERROR, ID_NOT_PROVIDED));
        promise.fail(registerResponse.toString());
      }
    } else {
      /* Handle Request Error */
      LOGGER.error("Error : Bad Request");
      registerResponse.clear().mergeIn(getResponseJson(BAD_REQUEST_CODE, ERROR, BAD_REQUEST));
      promise.fail(registerResponse.toString());
    }
    return promise.future();
  }

  Future<JsonObject> deleteAdapter(JsonObject json, String vhost) {
    LOGGER.debug("Info : RabbitClient#deleteAdapter() started");
    Promise<JsonObject> promise = Promise.promise();
    JsonObject finalResponse = new JsonObject();
    //System.out.println(json.toString());
    Future<JsonObject> result = getExchange(json, vhost);
    result.onComplete(resultHandler -> {
      if (resultHandler.succeeded()) {
        int status = resultHandler.result().getInteger("type");
        if (status == 200) {
          String exchangeID = json.getString("id");
          client.exchangeDelete(exchangeID, rh -> {
            if (rh.succeeded()) {
              LOGGER.debug("Info : " + exchangeID + " adaptor deleted successfully");
              finalResponse.mergeIn(getResponseJson(200, "success", "adaptor deleted"));
            } else if (rh.failed()) {
              finalResponse.clear()
                  .mergeIn(getResponseJson(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Adaptor deleted",
                      rh.cause().toString()));
              LOGGER.error("Error : Adaptor deletion failed cause - " + rh.cause());
              promise.fail(finalResponse.toString());
            } else {
              LOGGER.error("Error : Something wrong in deleting adaptor" + rh.cause());
              finalResponse.mergeIn(getResponseJson(400, "bad request", "nothing to delete"));
              promise.fail(finalResponse.toString());
            }
            promise.complete(finalResponse);
          });

        } else if (status == 404) { // exchange not found
          finalResponse.clear().mergeIn(
              getResponseJson(status, "not found", resultHandler.result().getString("detail")));
          LOGGER.error("Error : Exchange not found cause ");
          promise.fail(finalResponse.toString());
        } else { // some other issue
          LOGGER.error("Error : Bad request");
          finalResponse.mergeIn(getResponseJson(400, "bad request", "nothing to delete"));
          promise.fail(finalResponse.toString());
        }
      }
      if (resultHandler.failed()) {
        LOGGER.error("Error : deleteAdaptor - resultHandler failed : " + resultHandler.cause());
        finalResponse
            .mergeIn(getResponseJson(INTERNAL_ERROR_CODE, "bad request", "nothing to delete"));
        promise.fail(finalResponse.toString());

      }
    });
    return promise.future();
  }

  /**
   * The createUserIfNotExist implements the create user if does not exist.
   * 
   * @param userName which is a String
   * @param vhost which is a String
   * @return response which is a Future object of promise of Json type
   **/

  Future<JsonObject> createUserIfNotExist(String userName, String vhost) {
    LOGGER.debug("Info : RabbitClient#createUserIfNotPresent() started");
    Promise<JsonObject> promise = Promise.promise();
    /* Get domain, shaUsername from userName */
    String domain = userName.substring(userName.indexOf("@") + 1, userName.length());
    String shaUsername = domain + "/" + Util.getSha(userName);
    String password = Util.randomPassword.get();
    // This API requires user name in path parameter. Encode the username as it
    // contains a "/"
    String url = "/api/users/" + encodeValue(shaUsername);
    /* Check if user exists */
    JsonObject response = new JsonObject();
    webClient.requestAsync(REQUEST_GET, url).onComplete(reply -> {
      if (reply.succeeded()) {
        /* Check if user not found */
        if (reply.result().statusCode() == HttpStatus.SC_NOT_FOUND) {
          LOGGER.debug("Success : User not found. creating user");
          /* Create new user */
          Future<JsonObject> userCreated = createUser(shaUsername, password, vhost, url);
          userCreated.onComplete(handler -> {
            if (handler.succeeded()) {
              /* Handle the response */
              JsonObject result = handler.result();
              response.put(SHA_USER_NAME, shaUsername);
              response.put(APIKEY, password);
              response.put(TYPE, result.getInteger("type"));
              response.put(TITLE, result.getString("title"));
              response.put(DETAILS, result.getString("detail"));
              response.put(VHOST_PERMISSIONS, vhost);
              promise.complete(response);
            } else {
              LOGGER.error("Error : Error in user creation. Cause : " + handler.cause());
              response.mergeIn(getResponseJson(INTERNAL_ERROR_CODE, ERROR, USER_CREATION_ERROR));
              promise.fail(response.toString());
            }
          });

        } else if (reply.result().statusCode() == HttpStatus.SC_OK) {
          // user exists , So something useful can be done here
          /* Handle the response if a user exists */
          JsonObject readDbResponse = new JsonObject();
          Future<JsonObject> getUserApiKey = getUserInDb(shaUsername);

          getUserApiKey.onComplete(getUserApiKeyHandler -> {
            if (getUserApiKeyHandler.succeeded()) {
              LOGGER.info("DATABASE_READ_SUCCESS");
              String apiKey = getUserApiKey.result().getString(APIKEY);
              readDbResponse.put(SHA_USER_NAME, shaUsername);
              readDbResponse.put(APIKEY, apiKey);
              readDbResponse.mergeIn(
                  getResponseJson(SUCCESS_CODE, DATABASE_READ_SUCCESS, DATABASE_READ_SUCCESS));
              readDbResponse.put(VHOST_PERMISSIONS, vhost);
              promise.complete(readDbResponse);
            } else {
              LOGGER.info("DATABASE_READ_FAILURE");
              readDbResponse
                  .mergeIn(getResponseJson(INTERNAL_ERROR_CODE, ERROR, DATABASE_READ_FAILURE));
              promise.fail(readDbResponse.toString());
            }
          });
        }

      } else {
        /* Handle API error */
        LOGGER.error(
            "Error : Something went wrong while finding user using mgmt API: " + reply.cause());
        promise.fail(reply.cause().toString());
      }
    });
    return promise.future();

  }


  /**
   * CreateUserIfNotPresent's helper method which creates user if not present.
   * 
   * @param userName which is a String
   * @param vhost which is a String
   * @return response which is a Future object of promise of Json type
   **/
  Future<JsonObject> createUser(String shaUsername, String password, String vhost, String url) {
    LOGGER.debug("Info : RabbitClient#createUser() started");
    Promise<JsonObject> promise = Promise.promise();
    JsonObject response = new JsonObject();
    JsonObject arg = new JsonObject();
    arg.put(PASSWORD, password);
    arg.put(TAGS, NONE);

    webClient.requestAsync(REQUEST_PUT, url, arg).onComplete(ar -> {
      if (ar.succeeded()) {
        /* Check if user is created */
        if (ar.result().statusCode() == HttpStatus.SC_CREATED) {
          LOGGER.info("createUserRequest success");
          response.put(SHA_USER_NAME, shaUsername);
          response.put(PASSWORD, password);
          LOGGER.debug("Info : user created successfully");
          // set permissions to vhost for newly created user
          Future<JsonObject> vhostPermission = setVhostPermissions(shaUsername, vhost);
          vhostPermission.onComplete(handler -> {
            if (handler.succeeded()) {
              response.mergeIn(getResponseJson(SUCCESS_CODE, VHOST_PERMISSIONS,
                  handler.result().getString(DETAIL)));
              // Call the DB method to store username and password
              Future<JsonObject> createUserinDb = createUserInDb(shaUsername, password);
              createUserinDb.onComplete(createUserinDbHandler -> {
                if (createUserinDbHandler.succeeded()) {
                  promise.complete(response);
                } else {
                  /* Handle error */
                  LOGGER.error("Error : error in saving credentials. Cause : "
                      + createUserinDbHandler.cause());
                  promise.fail("Error : error in saving credentials");
                }
              });
            } else {
              /* Handle error */
              LOGGER.error("Error : error in setting vhostPermissions. Cause : " + handler.cause());
              promise.fail("Error : error in setting vhostPermissions");
            }
          });

        } else {
          /* Handle error */
          LOGGER.error("Error : createUser method - Some network error. cause" + ar.cause());
          response.put(FAILURE, NETWORK_ISSUE);
          promise.fail(response.toString());
        }
      } else {
        /* Handle error */
        LOGGER
            .info("Error : Something went wrong while creating user using mgmt API :" + ar.cause());
        response.put(FAILURE, CHECK_CREDENTIALS);
        promise.fail(response.toString());
      }
    });
    return promise.future();
  }

  Future<JsonObject> createUserInDb(String shaUsername, String password) {
    LOGGER.debug("Info : RabbitClient#createUserInDb() started");
    Promise<JsonObject> promise = Promise.promise();
    JsonObject response = new JsonObject();

    String query = INSERT_DATABROKER_USER.replace("$1", shaUsername).replace("$2", password);
    //System.out.println(query);

    // Check in DB, get username and password
    pgSQLClient.executeAsync(query).onComplete(db -> {
      LOGGER.debug("Info : RabbitClient#createUserInDb()executeAsync completed");
      if (db.succeeded()) {
        LOGGER.debug("Info : RabbitClient#createUserInDb()executeAsync success");
        response.put("status", "success");
        promise.complete(response);
      } else {
        LOGGER.fatal("Fail : RabbitClient#createUserInDb()executeAsync failed");
        promise.fail("Error : Write to database failed");
      }
    });
    return promise.future();
  }

  Future<JsonObject> getUserInDb(String shaUsername) {
    LOGGER.debug("Info : RabbitClient#getUserInDb() started");

    Promise<JsonObject> promise = Promise.promise();
    JsonObject response = new JsonObject();
    String query = SELECT_DATABROKER_USER.replace("$1", shaUsername);
    LOGGER.debug("Info : " + query);
    // Check in DB, get username and password
    pgSQLClient.executeAsync(query).onComplete(db -> {
      LOGGER.debug("Info : RabbitClient#getUserInDb()executeAsync completed");
      if (db.succeeded()) {
        LOGGER.debug("Info : RabbitClient#getUserInDb()executeAsync success");
        String apiKey = null;
        // Get the apiKey
        RowSet<Row> result = db.result();
        if (db.result().size() > 0) {
          for (Row row : result) {
            apiKey = row.getString(1);
          }
        }
        response.put(APIKEY, apiKey);
        promise.complete(response);
      } else {
        LOGGER.fatal("Fail : RabbitClient#getUserInDb()executeAsync failed");
        promise.fail("Error : Get ID from database failed");
      }
    });
    return promise.future();
  }

  /**
   * set topic permissions.
   * 
   * @param vhost which is a String
   * @param adaptorID which is a String
   * @param shaUsername which is a String
   * @return response which is a Future object of promise of Json type
   **/
  private Future<JsonObject> setTopicPermissions(String vhost, String adaptorID, String userID) {
    LOGGER.debug("Info : RabbitClient#setTopicPermissions() started");
    String url = "/api/permissions/" + vhost + "/" + encodeValue(userID);
    JsonObject param = new JsonObject();
    // set all mandatory fields
    param.put(EXCHANGE, adaptorID);
    param.put(WRITE, ALLOW);
    param.put(READ, DENY);
    param.put(CONFIGURE, DENY);

    Promise<JsonObject> promise = Promise.promise();
    JsonObject response = new JsonObject();
    webClient.requestAsync(REQUEST_PUT, url, param).onComplete(result -> {
      if (result.succeeded()) {
        /* Check if request was a success */
        if (result.result().statusCode() == HttpStatus.SC_CREATED) {
          response.mergeIn(
              getResponseJson(SUCCESS_CODE, TOPIC_PERMISSION, TOPIC_PERMISSION_SET_SUCCESS));
          LOGGER.debug("Success : Topic permission set");
          promise.complete(response);
        } else if (result.result()
            .statusCode() == HttpStatus.SC_NO_CONTENT) { /* Check if request was already served */
          response.mergeIn(
              getResponseJson(SUCCESS_CODE, TOPIC_PERMISSION, TOPIC_PERMISSION_ALREADY_SET));
          promise.complete(response);
        } else { /* Check if request has an error */
          LOGGER.error(
              "Error : error in setting topic permissions" + result.result().statusMessage());
          response.mergeIn(
              getResponseJson(INTERNAL_ERROR_CODE, TOPIC_PERMISSION, TOPIC_PERMISSION_SET_ERROR));
          promise.fail(response.toString());
        }
      } else { /* Check if request has an error */
        LOGGER.error("Error : error in setting topic permission : " + result.cause());
        response.mergeIn(
            getResponseJson(INTERNAL_ERROR_CODE, TOPIC_PERMISSION, TOPIC_PERMISSION_SET_ERROR));
        promise.fail(response.toString());
      }
    });
    return promise.future();
  }

  /**
   * set vhost permissions for given userName.
   * 
   * @param shaUsername which is a String
   * @param vhost which is a String
   * @return response which is a Future object of promise of Json type
   **/
  private Future<JsonObject> setVhostPermissions(String shaUsername, String vhost) {
    LOGGER.debug("Info : RabbitClient#setVhostPermissions() started");
    /* Construct URL to use */
    String url = "/api/permissions/" + vhost + "/" + encodeValue(shaUsername);
    JsonObject vhostPermissions = new JsonObject();
    // all keys are mandatory. empty strings used for configure,read as not
    // permitted.
    vhostPermissions.put(CONFIGURE, DENY);
    vhostPermissions.put(WRITE, ALLOW);
    vhostPermissions.put(READ, ALLOW);
    Promise<JsonObject> promise = Promise.promise();
    /* Construct a response object */
    JsonObject vhostPermissionResponse = new JsonObject();
    webClient.requestAsync(REQUEST_PUT, url, vhostPermissions).onComplete(handler -> {
      if (handler.succeeded()) {
        /* Check if permission was set */
        if (handler.result().statusCode() == HttpStatus.SC_CREATED) {
          LOGGER.debug("Success :write permission set for user [ " + shaUsername + " ] in vHost [ "
              + vhost + "]");
          vhostPermissionResponse
              .mergeIn(getResponseJson(SUCCESS_CODE, VHOST_PERMISSIONS, VHOST_PERMISSIONS_WRITE));
          promise.complete(vhostPermissionResponse);
        } else {
          LOGGER.error("Error : error in write permission set for user [ " + shaUsername
              + " ] in vHost [ " + vhost + " ]");
          vhostPermissionResponse.mergeIn(
              getResponseJson(INTERNAL_ERROR_CODE, VHOST_PERMISSIONS, VHOST_PERMISSION_SET_ERROR));
          promise.fail(vhostPermissions.toString());
        }
      } else {
        /* Check if request has an error */
        LOGGER.error("Error : error in write permission set for user [ " + shaUsername
            + " ] in vHost [ " + vhost + " ]");
        vhostPermissionResponse.mergeIn(
            getResponseJson(INTERNAL_ERROR_CODE, VHOST_PERMISSIONS, VHOST_PERMISSION_SET_ERROR));
        promise.fail(vhostPermissions.toString());
      }
    });
    return promise.future();
  }

  /**
   * Helper method which bind registered exchange with predefined queues
   * 
   * @param adaptorID which is a String object
   * 
   * @return response which is a Future object of promise of Json type
   */
  Future<JsonObject> queueBinding(String adaptorID) {
    LOGGER.info("RabbitClient#queueBinding() method started");
    Promise<JsonObject> promise = Promise.promise();
    String topics = adaptorID + DATA_WILDCARD_ROUTINGKEY;
    bindQueue(QUEUE_DATA, adaptorID, topics)
        .compose(queueDataResult -> bindQueue(QUEUE_ADAPTOR_LOGS, adaptorID, adaptorID + HEARTBEAT))
        .compose(
            heartBeatResult -> bindQueue(QUEUE_ADAPTOR_LOGS, adaptorID, adaptorID + DATA_ISSUE))
        .compose(dataIssueResult -> bindQueue(QUEUE_ADAPTOR_LOGS, adaptorID,
            adaptorID + DOWNSTREAM_ISSUE))
        .onSuccess(successHandler -> {
          JsonObject response = new JsonObject();
          response.mergeIn(getResponseJson(SUCCESS_CODE, "Queue_Database",
              QUEUE_DATA + " queue bound to " + adaptorID));
          LOGGER.debug("Success : " + response);
          promise.complete(response);
        }).onFailure(failureHandler -> {
          LOGGER.error("Error : queue bind error : " + failureHandler.getCause().toString());
          JsonObject response = getResponseJson(INTERNAL_ERROR_CODE, ERROR, QUEUE_BIND_ERROR);
          promise.fail(response.toString());
        });
    return promise.future();
  }

  Future<Void> bindQueue(String data, String adaptorID, String topics) {
    LOGGER.debug("Info : RabbitClient#bindQueue() started");
    LOGGER.debug("Info : data : " + data + " adaptorID : " + adaptorID + " topics : " + topics);
    Promise<Void> promise = Promise.promise();
    client.queueBind(data, adaptorID, topics, handler -> {
      if (handler.succeeded()) {
        promise.complete();
      } else {
        LOGGER.error("Error : Queue" + data + " binding error : " + handler.cause());
        promise.fail(handler.cause());
      }
    });
    return promise.future();
  }

  public RabbitMQClient getRabbitMQClient() {
    return this.client;
  }
}
