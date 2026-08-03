package autismclient.util.mm.relay;

import java.util.function.Consumer;

public interface Relay {
    String name();

    RelayStatus status();

    void publish(String topic, byte[] frame);

    default void publish(String topic, byte[] frame, boolean durable) { publish(topic, frame); }

    void subscribe(String topic, Consumer<byte[]> onFrame);

    void unsubscribe(String topic);

    default void reconnect() {}

    void close();
}
