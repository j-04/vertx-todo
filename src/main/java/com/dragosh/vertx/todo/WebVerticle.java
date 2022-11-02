package com.dragosh.vertx.todo;

import io.vertx.core.AbstractVerticle;

import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;

public class WebVerticle extends AbstractVerticle {
    @Override
    public void start(Promise<Void> promise) {
        Router router = Router.router(vertx);
        int port = (int) JsonPointer
                .from("/http/port")
                .queryJson(config());
        
        router.route().handler(LoggerHandler.create());
        router.route().handler(BodyHandler.create());

        router.get("/api/v1/todos").handler(this::getTodos);
        router.get("/api/v1/todos/:uuid").handler(this::getOneTodo);
        router.post("/api/v1/todos").handler(this::saveTodo).failureHandler(this::handleFailure);
        router.delete("/api/v1/todos/:uuid").handler(this::deleteTodo);
        router.put("/api/v1/todos/:uuid").handler(this::completeTodo);

        router.route().handler(this::handleDefault);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(server -> {
                    promise.complete();
                })
                .onFailure(thr -> {
                    promise.fail(thr.getMessage());
                });
    }

    void handleFailure(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        ctx.response().setStatusCode(500).setStatusMessage("Server internal error:" + failure.getMessage()).end();
    }

    void completeTodo(RoutingContext ctx) {
        EventBus eb = vertx.eventBus();
        String uuid = ctx.pathParam("uuid");

        eb.send(DBVerticle.COMPLETE_TODO_ADDR, uuid);
        ctx.response().end();
    }

    void deleteTodo(RoutingContext ctx) {
        EventBus eb = vertx.eventBus();
        String uuid = ctx.pathParam("uuid");
        
        eb.send(DBVerticle.DELETE_TODO_ADDR, uuid);
        ctx.response().end();
    }

    void saveTodo(RoutingContext ctx) {
        EventBus eb = vertx.eventBus();
        JsonObject body = ctx.body().asJsonObject();
        System.out.println(body);
        eb.request(DBVerticle.SAVE_TODO_ADDR, body, reply -> {
            if (reply.failed()) {
                System.out.println("Saving process is faield: " + reply.result());
                ctx.response().end(Json.encode(reply.result()));
            } else {
                ctx.response().end();
            }
        });
    }

    void getOneTodo(RoutingContext ctx) {
        EventBus eb = vertx.eventBus();
        String uuid = ctx.pathParam("uuid");
        eb.request(DBVerticle.GET_ONE_TODO_ADDR, uuid, reply -> {
            if (reply.succeeded()) {
                ctx.response().end(Json.encodePrettily(reply.result().body()));
            } else {
                handleFailure(ctx, reply.cause());
            }
        });
    }

    void getTodos(RoutingContext ctx) {
        EventBus eb = vertx.eventBus();
        eb.request(DBVerticle.GET_TODOS_ADDR, "", reply -> {
            if (reply.succeeded()) {
                ctx.response().end(Json.encodePrettily(reply.result().body()));
            } else {
                handleFailure(ctx, reply.cause());
            }
        });
    }

    void handleFailure(RoutingContext ctx, Throwable cause) {
        ctx.response()
                    .end(Json.encodePrettily(
                        new JsonObject().put("message",
                                "Ooops. Couldnt get todos. " + cause.getMessage())));
    }

    void handleDefault(RoutingContext ctx) {
        ctx.response()
                .end(Json.encodePrettily(new JsonObject().put("message", "Hello!")));
    }
}
