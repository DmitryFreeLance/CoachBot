package com.example.coachbot.repo;

import com.example.coachbot.Db;

import java.sql.*;

public class SettingsRepo {
    public static String get(String key, String def) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT v FROM settings WHERE k=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString(1); }
        }
        return def;
    }

    public static void set(String key, String value) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO settings(k,v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v")) {
            ps.setString(1, key); ps.setString(2, value); ps.executeUpdate();
        }
    }
}