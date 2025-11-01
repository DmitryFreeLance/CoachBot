package com.example.coachbot.repo;

import com.example.coachbot.Db;

import java.sql.*;
import java.util.*;

public class GroupRepo {

    public static boolean addToAdmin(String adminId, String userId) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("INSERT INTO admin_groups(admin_id,user_id) VALUES(?,?)")) {
            ps.setString(1, adminId); ps.setString(2, userId);
            ps.executeUpdate();
            return true;
        } catch (SQLException ex) {
            return false; // уже в группе у кого-то
        }
    }

    public static boolean removeFromAdmin(String adminId, String userId) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM admin_groups WHERE admin_id=? AND user_id=?")) {
            ps.setString(1, adminId); ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public static String adminOf(String userId) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT admin_id FROM admin_groups WHERE user_id=?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    public static List<String> usersOfAdmin(String adminId, int limit, int offset) throws Exception {
        List<String> ids = new ArrayList<>();
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT user_id FROM admin_groups WHERE admin_id=? ORDER BY user_id LIMIT ? OFFSET ?")) {
            ps.setString(1, adminId); ps.setInt(2, limit); ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) ids.add(rs.getString(1)); }
        }
        return ids;
    }

    public static int countUsersOfAdmin(String adminId) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM admin_groups WHERE admin_id=?")) {
            ps.setString(1, adminId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next()?rs.getInt(1):0; }
        }
    }
}