package dev.adrian.chesttracker.client.net;

import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.net.QueryDto;
import dev.adrian.chesttracker.net.ChestTrackerPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ServerLink {

    public enum State {
        WAITING,
        PRESENT,
        ABSENT
    }

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    private static final Map<Integer, Pending<?>> PENDING = new ConcurrentHashMap<>();

    private static volatile State state = State.ABSENT;
    private static volatile boolean canQuery;
    private static volatile long joinedAt;

    private record Pending<T>(Class<T> type, CompletableFuture<T> future, T empty, long deadline) {}

    private ServerLink() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ChestTrackerPayloads.HelloPayload.TYPE,
                (payload, context) -> {
                    if (payload.hello().protocolVersion() != QueryDto.Hello.PROTOCOL_VERSION) {
                        state = State.ABSENT;
                        return;
                    }
                    state = State.PRESENT;
                    canQuery = payload.hello().canQuery();
                });

        ClientPlayNetworking.registerGlobalReceiver(
                ChestTrackerPayloads.SummaryResponsePayload.TYPE,
                (payload, context) -> deliver(payload.response().requestId(), payload.response()));

        ClientPlayNetworking.registerGlobalReceiver(
                ChestTrackerPayloads.ContainerResponsePayload.TYPE,
                (payload, context) -> deliver(payload.response().requestId(), payload.response()));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            failAllPending();
            state = State.WAITING;
            canQuery = false;
            joinedAt = System.currentTimeMillis();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            failAllPending();
            state = State.ABSENT;
            canQuery = false;
        });
    }

    public static State state() {
        return state;
    }

    public static boolean canQuery() {
        return state == State.PRESENT && canQuery;
    }

    public static void tick() {
        long now = System.currentTimeMillis();

        if (state == State.WAITING && now - joinedAt > graceMs()) {
            state = State.ABSENT;
        }

        if (PENDING.isEmpty()) return;
        for (Iterator<Map.Entry<Integer, Pending<?>>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Pending<?>> entry = it.next();
            if (now < entry.getValue().deadline()) continue;
            it.remove();
            completeEmpty(entry.getValue());
        }
    }

    private static long graceMs() {
        return Math.max(250, ChestTrackerConfig.get().serverHelloTimeoutMs);
    }

    public static int nextRequestId() {
        return NEXT_ID.getAndIncrement();
    }

    public static CompletableFuture<QueryDto.SummaryResponse> summarise(QueryDto.SummaryRequest request) {
        return send(request.requestId(), QueryDto.SummaryResponse.class,
                new ChestTrackerPayloads.SummaryRequestPayload(request),
                new QueryDto.SummaryResponse(request.requestId(), List.of()));
    }

    public static CompletableFuture<QueryDto.ContainerResponse> containers(QueryDto.ContainerRequest request) {
        return send(request.requestId(), QueryDto.ContainerResponse.class,
                new ChestTrackerPayloads.ContainerRequestPayload(request),
                new QueryDto.ContainerResponse(request.requestId(), List.of()));
    }

    private static <T> CompletableFuture<T> send(int requestId, Class<T> type,
                                                 CustomPacketPayload payload, T empty) {
        if (!ClientPlayNetworking.canSend(payload.type())) {
            return CompletableFuture.completedFuture(empty);
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        PENDING.put(requestId, new Pending<>(type, future, empty, System.currentTimeMillis() + graceMs()));
        ClientPlayNetworking.send(payload);
        return future;
    }

    private static void deliver(int requestId, Object response) {
        if (state == State.WAITING) {
            state = State.PRESENT;
            canQuery = true;
        }

        Pending<?> pending = PENDING.get(requestId);
        if (pending == null) return;

        if (!pending.type().isInstance(response)) return;

        PENDING.remove(requestId);
        complete(pending, response);
    }

    private static <T> void complete(Pending<T> pending, Object response) {
        pending.future().complete(pending.type().cast(response));
    }

    private static <T> void completeEmpty(Pending<T> pending) {
        pending.future().complete(pending.empty());
    }

    private static void failAllPending() {
        for (Iterator<Map.Entry<Integer, Pending<?>>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Pending<?> pending = it.next().getValue();
            it.remove();
            completeEmpty(pending);
        }
    }
}
