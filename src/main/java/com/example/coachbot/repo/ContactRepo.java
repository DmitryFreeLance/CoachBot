package com.example.coachbot.repo;

import com.example.coachbot.Db;

import java.sql.*;

public class ContactRepo {
    public static void set(String adminId, String text) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("INSERT INTO contacts(admin_id,text) VALUES(?,?) ON CONFLICT(admin_id) DO UPDATE SET text=excluded.text, updated_at=CURRENT_TIMESTAMP")) {
            ps.setString(1, adminId); ps.setString(2, text); ps.executeUpdate();
        }
    }

    public static String get(String adminId) throws Exception {
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement("SELECT text FROM contacts WHERE admin_id=?")) {
            ps.setString(1, adminId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next()?rs.getString(1):null; }
        }
    }
}