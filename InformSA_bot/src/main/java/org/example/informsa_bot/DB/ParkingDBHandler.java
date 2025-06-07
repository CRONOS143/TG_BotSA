package org.example.informsa_bot.DB;

import java.sql.*;
import java.util.*;

public class ParkingDBHandler {
    private static final String DB_URL = "jdbc:sqlite:parking.db";

    public ParkingDBHandler() {
        initDatabase();
    }

    // Создаём базу и таблицу, если их нет
    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            String sqlCreateTable = """
                CREATE TABLE IF NOT EXISTS parking_spots (
                    spot_id INTEGER PRIMARY KEY,
                    booked_by_username TEXT,
                    booked_by_phone TEXT
                )
                """;
            stmt.execute(sqlCreateTable);

            // Проверяем, есть ли записи
            String countQuery = "SELECT COUNT(*) FROM parking_spots";
            try (ResultSet rs = stmt.executeQuery(countQuery)) {
                if (rs.next() && rs.getInt(1) == 0) {
                    // Если пусто, добавляем 20 мест
                    for (int i = 1; i <= 20; i++) {
                        String insert = "INSERT INTO parking_spots (spot_id) VALUES (?)";
                        try (PreparedStatement ps = conn.prepareStatement(insert)) {
                            ps.setInt(1, i);
                            ps.executeUpdate();
                        }
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<ParkingSpot> getAllSpots() {
        List<ParkingSpot> spots = new ArrayList<>();
        String sql = "SELECT spot_id, booked_by_username, booked_by_phone FROM parking_spots ORDER BY spot_id";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ParkingSpot spot = new ParkingSpot(
                        rs.getInt("spot_id"),
                        rs.getString("booked_by_username"),
                        rs.getString("booked_by_phone")
                );
                spots.add(spot);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return spots;
    }

    public ParkingSpot getSpotById(int spotId) {
        String sql = "SELECT spot_id, booked_by_username, booked_by_phone FROM parking_spots WHERE spot_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, spotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ParkingSpot(
                            rs.getInt("spot_id"),
                            rs.getString("booked_by_username"),
                            rs.getString("booked_by_phone")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean bookSpot(int spotId, String username, String phone) {
        String sql = "UPDATE parking_spots SET booked_by_username = ?, booked_by_phone = ? WHERE spot_id = ? AND booked_by_username IS NULL";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, phone);
            ps.setInt(3, spotId);
            int affected = ps.executeUpdate();
            return affected == 1; // true если обновление прошло
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean cancelBookingByUser(String username) {
        String sql = "UPDATE parking_spots SET booked_by_username = NULL, booked_by_phone = NULL WHERE booked_by_username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public ParkingSpot getBookingByUser(String username) {
        String sql = "SELECT spot_id, booked_by_username, booked_by_phone FROM parking_spots WHERE booked_by_username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ParkingSpot(
                            rs.getInt("spot_id"),
                            rs.getString("booked_by_username"),
                            rs.getString("booked_by_phone")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
