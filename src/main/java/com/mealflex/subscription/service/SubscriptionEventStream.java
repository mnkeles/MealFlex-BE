package com.mealflex.subscription.service;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SubscriptionEventStream {
    private final ConcurrentMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long storeId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(storeId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> remove(storeId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException | IllegalStateException exception) {
            remove.run();
        }
        return emitter;
    }

    public void publish(Long storeId, String event, Object data) {
        CopyOnWriteArrayList<SseEmitter> storeEmitters = emitters.get(storeId);
        if (storeEmitters == null) return;
        for (SseEmitter emitter : storeEmitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException | IllegalStateException exception) {
                remove(storeId, emitter);
                try {
                    emitter.complete();
                } catch (IllegalStateException ignored) {
                    // İstemci bağlantısı zaten kapanmış olabilir.
                }
            }
        }
    }

    private void remove(Long storeId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> storeEmitters = emitters.get(storeId);
        if (storeEmitters == null) return;
        storeEmitters.remove(emitter);
        if (storeEmitters.isEmpty()) emitters.remove(storeId, storeEmitters);
    }
}
