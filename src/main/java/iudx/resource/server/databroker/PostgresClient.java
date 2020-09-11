package iudx.resource.server.databroker;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;

public class PostgresClient {
  private static final Logger LOGGER = LogManager.getLogger(PostgresClient.class);

  private PgPool pgPool;

  public PostgresClient(Vertx vertx, PgConnectOptions pgConnectOptions,
      PoolOptions connectionPoolOptions) {
    this.pgPool = PgPool.pool(vertx, pgConnectOptions, connectionPoolOptions);
  }

  public Future<RowSet<Row>> executeAsync(String preparedQuerySQL, Tuple args) {
    LOGGER.info("PostgresQLClient#executeAsync() started");
    LOGGER.info(args);
    Promise<RowSet<Row>> promise = Promise.promise();
    pgPool.getConnection(connectionHandler -> {
      if (connectionHandler.succeeded()) {
        SqlConnection pgConnection = connectionHandler.result();
        pgConnection.preparedQuery(preparedQuerySQL).execute(args, executeHandler -> {
          LOGGER.info("executeHandler");
          if (executeHandler.succeeded()) {
            promise.complete(executeHandler.result());
          } else {
            pgConnection.close();
            LOGGER.error(executeHandler.cause());
            promise.fail(executeHandler.cause());
          }
        });
      }
    });
    return promise.future();
  }

  public Future<RowSet<Row>> executeAsync(String preparedQuerySQL) {
    LOGGER.info("PostgresQLClient#executeAsync() started");
    Promise<RowSet<Row>> promise = Promise.promise();
    pgPool.getConnection(connectionHandler -> {
      if (connectionHandler.succeeded()) {
        SqlConnection pgConnection = connectionHandler.result();
        pgConnection.preparedQuery(preparedQuerySQL).execute(executeHandler -> {
          if (executeHandler.succeeded()) {
            promise.complete(executeHandler.result());
          } else {
            promise.fail(executeHandler.cause());
          }
        });
      }
    });
    return promise.future();
  }
}