package com.dragosh.vertx.todo;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

public class MainVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

    private final String verticleId = UUID.randomUUID().toString();

    private final JsonObject loadedConfig = new JsonObject();

    @Override
    public void start(Promise<Void> start) {
        doConfig()
                .compose(this::saveConfig)
                .compose(this::deployVerticles)
                .onSuccess(handler -> handleSuccess(start))
                .onFailure(exception -> handleFail(start, exception)); 
    }

    private Future<Void> handleSuccess(Promise<Void> promise) {
        log.info("Verticles are successfully started up!");
        promise.complete();
        return promise.future();
    }

    private void handleFail(Promise<Void> promise, Throwable throwable) {
        log.error("Failed to start up MainVerticle: {}", throwable.getMessage());
        promise.fail(throwable);
    }

    private Future<Void> deployVerticles(Void unused) {
        DeploymentOptions options = new DeploymentOptions()
                .setConfig(loadedConfig);
        
        Future<String> webVerticleFuture = Future.future(promise -> vertx.deployVerticle(new WebVerticle(), options, promise));
        Future<String> dbVerticle = Future.future(promise -> vertx.deployVerticle(new DBVerticle(), options, promise));
        
        return CompositeFuture.all(webVerticleFuture, dbVerticle).mapEmpty();
    }

    private Future<Void> saveConfig(JsonObject cfg) {
        loadedConfig.mergeIn(cfg);
        log.info("Was loaded config with these parameters: {}", Json.encodePrettily(loadedConfig));
        return Future.succeededFuture();
    }

    private Future<JsonObject> doConfig() {
        ConfigStoreOptions defaultConfig = new ConfigStoreOptions()
                .setType("file")
                .setFormat("yaml")
                .setConfig(new JsonObject().put("path", "./config.yaml"));

        ConfigStoreOptions cliConfig = new ConfigStoreOptions()
                .setType("json")
                .setConfig(config());

        ConfigRetrieverOptions configRetrieverOptions = new ConfigRetrieverOptions()
                .addStore(defaultConfig)
                .addStore(cliConfig);

        ConfigRetriever retriever = ConfigRetriever.create(vertx, configRetrieverOptions);
        return Future.future(retriever::getConfig);
    }
}