package com.example.coachbot.repo;

import com.example.coachbot.Db;
import com.example.coachbot.Roles;

import java.sql.*;
import java.util.*;

public class UserRepo {

    public static void upsertUser(String tgId, String username, String firstName) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO users(tg_id, username, first_name) VALUES(?,?,?) " +
                             "ON CONFLICT(tg_id) DO UPDATE SET username=excluded.username, first_name=excluded.first_name")) {
            ps.setString(1, tgId); ps.setString(2, username); ps.setString(3, firstName);
            ps.executeUpdate();
        }
    }

    public static boolean exists(String tgId) throws Exception {
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE tg_id=?")) {
            ps.setString(1, tgId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public static Roles role(String tgId) throws Exception {
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement("SELECT role FROM users WHERE tg_id=?")) {
            ps.setString(1, tgId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Roles.valueOf(rs.getString(1));
            }
        }
        return Roles.USER;
    }

    public static void setRole(String tgId, Roles role) throws Exception {
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement("UPDATE users SET role=? WHERE tg_id=?")) {
            ps.setString(1, role.name()); ps.setString(2, tgId); ps.executeUpdate();
        }
    }

    public static List<String> allActiveUsers() throws Exception {
        List<String> ids = new ArrayList<>();
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement("SELECT tg_id FROM users WHERE role='USER'")) {
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) ids.add(rs.getString(1)); }
        }
        return ids;
    }

    public static List<String> allUsersPaged(int limit, int offset) throws Exception {
        List<String> ids = new ArrayList<>();
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement("SELECT tg_id FROM users WHERE role='USER' ORDER BY added_at DESC LIMIT ? OFFSET ?")) {
            ps.setInt(1, limit); ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) ids.add(rs.getString(1)); }
        }
        return ids;
    }

    public static int countUsers() throws Exception {
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM users WHERE role='USER'")) {
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    public static void ensureAdmin(String tgId) throws Exception {
        if (!exists(tgId)) {
            try (Connection c = Db.connect();
                 PreparedStatement ps = c.prepareStatement("INSERT INTO users(tg_id, role) VALUES(?, 'ADMIN')")) {
                ps.setString(1, tgId); ps.executeUpdate();
            }
        } else setRole(tgId, Roles.ADMIN);
    }
}