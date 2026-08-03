package autismclient.api;

public record AddonRegistrationResult(boolean accepted, String kind, String id, String reason) {

    public static AddonRegistrationResult accepted(String kind, String id) {
        return new AddonRegistrationResult(true, clean(kind), clean(id), "Accepted");
    }

    public static AddonRegistrationResult rejected(String kind, String id, String reason) {
        return new AddonRegistrationResult(false, clean(kind), clean(id), clean(reason));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
