package autismclient.util.oresim;

import java.util.Objects;

public final class AutismOreSimSeedInput {

    public enum Status {
        EMPTY,
        VALID,
        INVALID
    }

    public record Result(Status status, Long value) {
        public Result {
            Objects.requireNonNull(status, "status");
            if ((status == Status.VALID) != (value != null)) {
                throw new IllegalArgumentException("Only a valid seed may carry a value");
            }
        }

        public boolean isValid() {
            return status == Status.VALID;
        }
    }

    private static final Result EMPTY = new Result(Status.EMPTY, null);
    private static final Result INVALID = new Result(Status.INVALID, null);

    private AutismOreSimSeedInput() {
    }

    public static Result parse(String raw) {
        if (raw == null) return EMPTY;
        String input = raw.strip();
        if (input.isEmpty()) return EMPTY;
        try {
            return new Result(Status.VALID, Long.parseLong(input));
        } catch (NumberFormatException ignored) {
            return INVALID;
        }
    }
}
