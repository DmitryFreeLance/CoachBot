package com.example.coachbot.repo;

import com.example.coachbot.Db;

import java.sql.*;

public class StateRepo {
    public static void set(String userId, String type, int step, String payload) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("INSERT INTO user_states(user_id,type,step,payload) VALUES(?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET type=excluded.type, step=excluded.step, payload=excluded.payload")) {
            ps.setString(1, userId); ps.setString(2, type); ps.setInt(3, step); ps.setString(4, payload); ps.executeUpdate();
        }
    }
    public static void clear(String userId) throws Exception {
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement("DELETE FROM user_states WHERE user_id=?")) {
            ps.setString(1, userId); ps.executeUpdate();
        }
    }
    public static State get(String userId) throws Exception {
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement("SELECT type,step,payload FROM user_states WHERE user_id=?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new State(rs.getString(1), rs.getInt(2), rs.getString(3));
            }
        }
        return null;
    }
    public record State(String type, int step, String payload) {}
}