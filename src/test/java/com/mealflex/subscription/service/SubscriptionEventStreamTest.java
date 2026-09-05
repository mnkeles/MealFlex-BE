package com.mealflex.subscription.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SubscriptionEventStreamTest {

    @Test
    @SuppressWarnings("unchecked")
    void disconnectedClientCannotBreakBusinessEventPublishing() throws Exception {
        SubscriptionEventStream stream = new SubscriptionEventStream();
        SseEmitter disconnectedEmitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(disconnectedEmitter).send(any(SseEmitter.SseEventBuilder.class));

        Field field = SubscriptionEventStream.class.getDeclaredField("emitters");
        field.setAccessible(true);
        ConcurrentMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters =
                (ConcurrentMap<Long, CopyOnWriteArrayList<SseEmitter>>) field.get(stream);
        emitters.put(2L, new CopyOnWriteArrayList<>(List.of(disconnectedEmitter)));

        assertThatCode(() -> stream.publish(2L, "delivery-change-requested", "payload"))
                .doesNotThrowAnyException();
        assertThat(emitters).doesNotContainKey(2L);
    }
}
