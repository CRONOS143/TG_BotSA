package org.example.informsa_bot.DB;

public class ParkingSpot {
    private final int spotId;
    private final String bookedByUsername;
    private final String bookedByPhone;

    public ParkingSpot(int spotId, String bookedByUsername, String bookedByPhone) {
        this.spotId = spotId;
        this.bookedByUsername = bookedByUsername;
        this.bookedByPhone = bookedByPhone;
    }

    public int getSpotId() {
        return spotId;
    }

    public String getBookedByUsername() {
        return bookedByUsername;
    }

    public String getBookedByPhone() {
        return bookedByPhone;
    }

    public boolean isBooked() {
        return bookedByUsername != null && !bookedByUsername.isEmpty();
    }
}
