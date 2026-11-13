package bao.gabriel.cognia.system.dto.auth;

public record AvailabilityResponse(boolean available, String message) {
    public static AvailabilityResponse ok() {
        return new AvailabilityResponse(true, null);
    }

    public static AvailabilityResponse fail(String message) {
        return new AvailabilityResponse(false, message);
    }
}
