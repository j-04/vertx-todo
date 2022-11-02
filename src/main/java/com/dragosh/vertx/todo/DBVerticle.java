package com.dragosh.vertx.todo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dragosh.vertx.todo.model.Todo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class DBVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(AbstractVerticle.class);
     
    public static final String GET_TODOS_ADDR = "com.dragosh.vertx.todo.get_todos_addr";
    public static final String GET_ONE_TODO_ADDR = "com.dragosh.vertx.todo.get_one_todo_addr";
    public static final String SAVE_TODO_ADDR = "com.dragosh.vertx.todo.save_todos_addr";
    public static final String DELETE_TODO_ADDR = "com.dragosh.vertx.todo.delete_todo_addr";
    public static final String COMPLETE_TODO_ADDR = "com.dragosh.vertx.todo.complete_todo_addr";

    private final String verticleId = UUID.randomUUID().toString();

    private final Map<String, Todo> database = new HashMap<>();

    @Override
    public void start(Promise<Void> promise) {
        EventBus eb = vertx.eventBus();
        eb.consumer(GET_TODOS_ADDR).handler(this::getTodos);
        eb.consumer(GET_ONE_TODO_ADDR).handler(this::getOneTodo);
        eb.consumer(SAVE_TODO_ADDR).handler(this::saveTodo);
        eb.consumer(DELETE_TODO_ADDR).handler(this::deleteTodo);
        eb.consumer(COMPLETE_TODO_ADDR).handler(this::completeTodo);
    }

    void getTodos(Message<Object> message) {
        log.info("Message is obtained by getTodos handler");
        List<JsonObject> todos = database.values().stream()
            .map(JsonObject::mapFrom)
            .collect(Collectors.toList());
        message.reply(new JsonArray(todos));
    }

    void getOneTodo(Message<Object> message) {
        if (message.body() == null) {
            message.fail(500, "Body is null");
        }
        
        String uuid = (String) message.body();

        log.info("Message is obtained by getOneTodo with message body {}", uuid);

        Optional.ofNullable(database.get(uuid)).ifPresentOrElse(todo -> {
            message.reply(JsonObject.mapFrom(todo));
        }, () -> {
            message.reply("");
        });
        
    }

    void saveTodo(Message<Object> message) {
        Todo todo = null;
        try {
            todo = ((JsonObject) message.body()).mapTo(Todo.class);
        } catch (DecodeException exception) {
            log.error("Failed on decoding process!");
            message.reply("");
        }

        String uuid = UUID.randomUUID().toString();
        todo.setId(uuid);
        
        log.info("Message is obtained by saveTodo handler. Saving todo {}.", todo);

        database.put(uuid, todo);
        message.reply("");
    }

    void deleteTodo(Message<Object> message) {
        if (message.body() == null) {
            message.fail(500, "Body is null");
        }

        String uuid = (String) message.body();

        log.info("Message is obtained by deleteTodo handler. Deleting the Todo with id {}", uuid);

        database.remove(uuid);
    }

    void completeTodo(Message<Object> message) {
        if (message.body() == null) {
            message.fail(500, "Body is null");
        }
        String uuid = (String) message.body();


        Optional.ofNullable(database.get(uuid)).ifPresent(todo -> {
            log.info("Message is obtained by completeTodo handler. Making the Todo {} completed.", todo);
            todo.setCompleted(true);
        });
    }
}