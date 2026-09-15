package dev.adrian.chestindex.client.net;

import dev.adrian.chestindex.config.ChestIndexConfig;
import dev.adrian.chestindex.core.net.QueryDto;
import dev.adrian.chestindex.net.ChestIndexPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

    private static final AtomicLong CHANGE_TOKEN = new AtomicLong();

    private record Pending<T>(Class<T> type, CompletableFuture<T> future, T empty, long deadline) {}

    private ServerLink() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ChestIndexPayloads.HelloPayload.TYPE,
                (payload, context) -> {
                    if (payload.hello().protocolVersion() != QueryDto.Hello.PROTOCOL_VERSION) {
                        state = State.ABSENT;
                        return;
                    }
                    state = State.PRESENT;
                    canQuery = payload.hello().canQuery();
                });

        ClientPlayNetworking.registerGlobalReceiver(
                ChestIndexPayloads.SummaryResponsePayload.TYPE,
                (payload, context) -> {
                    canQuery = payload.response().permitted();
                    deliver(payload.response().requestId(), payload.response());
                });

        ClientPlayNetworking.registerGlobalReceiver(
                ChestIndexPayloads.ContainerResponsePayload.TYPE,
                (payload, context) -> {
                    canQuery = payload.response().permitted();
                    deliver(payload.response().requestId(), payload.response());
                });

        ClientPlayNetworking.registerGlobalReceiver(
                ChestIndexPayloads.StatusResponsePayload.TYPE,
                (payload, context) -> deliver(payload.response().requestId(), payload.response()));

        ClientPlayNetworking.registerGlobalReceiver(
                ChestIndexPayloads.IndexChangedPayload.TYPE,
                (payload, context) -> CHANGE_TOKEN.incrementAndGet());

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

    public static long changeToken() {
        return CHANGE_TOKEN.get();
    }

    public static void setWatching(boolean watching) {
        if (state != State.PRESENT) return;
        if (!ClientPlayNetworking.canSend(ChestIndexPayloads.SubscribePayload.TYPE)) return;
        ClientPlayNetworking.send(new ChestIndexPayloads.SubscribePayload(watching));
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
        return Math.max(250, ChestIndexConfig.get().serverHelloTimeoutMs);
    }

    public static int nextRequestId() {
        return NEXT_ID.getAndIncrement();
    }

    public static CompletableFuture<QueryDto.SummaryResponse> summarise(QueryDto.SummaryRequest request) {
        return send(request.requestId(), QueryDto.SummaryResponse.class,
                new ChestIndexPayloads.SummaryRequestPayload(request),
                QueryDto.SummaryResponse.of(request.requestId(), List.of()));
    }

    public static CompletableFuture<QueryDto.StatusResponse> status(QueryDto.StatusRequest request) {
        return send(request.requestId(), QueryDto.StatusResponse.class,
                new ChestIndexPayloads.StatusRequestPayload(request),
                QueryDto.StatusResponse.empty(request.requestId()));
    }

    public static CompletableFuture<QueryDto.ContainerResponse> containers(QueryDto.ContainerRequest request) {
        return send(request.requestId(), QueryDto.ContainerResponse.class,
                new ChestIndexPayloads.ContainerRequestPayload(request),
                QueryDto.ContainerResponse.of(request.requestId(), List.of()));
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
        if (state == State.WAITING) state = State.PRESENT;

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
