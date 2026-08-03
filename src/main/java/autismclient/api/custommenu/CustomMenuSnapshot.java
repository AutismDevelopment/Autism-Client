package autismclient.api.custommenu;

import java.util.List;

public record CustomMenuSnapshot(
    String adapterId,
    String phase,
    long generation,
    String title,
    List<CustomMenuInput> inputs,
    List<CustomMenuButton> buttons,
    Object adapterState
) {
    public CustomMenuSnapshot {
        adapterId = adapterId == null ? "" : adapterId;
        phase = phase == null ? "" : phase;
        title = title == null ? "" : title;
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        buttons = buttons == null ? List.of() : List.copyOf(buttons);
    }

    public CustomMenuSnapshot withConnectionState(String newPhase, long newGeneration) {
        return new CustomMenuSnapshot(adapterId, newPhase, newGeneration, title, inputs, buttons, adapterState);
    }
}
