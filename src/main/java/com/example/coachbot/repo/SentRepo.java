package com.example.coachbot.repo;

import com.example.coachbot.Db;

import java.sql.*;
import java.time.LocalDate;

public class SentRepo {
    public static boolean notSentYet(String type, String userId, LocalDate date) throws Exception {
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement("SELECT 1 FROM sent_notifications WHERE type=? AND user_id=? AND date=?")) {
            ps.setString(1, type); ps.setString(2, userId); ps.setString(3, date.toString());
            try (ResultSet rs = ps.executeQuery()) { return !rs.next(); }
        }
    }
    public static void markSent(String type, String userId, LocalDate date) throws Exception {
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO sent_notifications(type,user_id,date) VALUES(?,?,?)")) {
            ps.setString(1, type); ps.setString(2, userId); ps.setString(3, date.toString());
            ps.executeUpdate();
        }
    }
}