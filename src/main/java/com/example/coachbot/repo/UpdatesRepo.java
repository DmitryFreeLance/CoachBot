package com.example.coachbot.repo;

import com.example.coachbot.Db;
import java.sql.*;

public class UpdatesRepo {
    public static boolean markProcessed(int updateId) throws Exception {
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement("INSERT INTO processed_updates(update_id) VALUES(?)")) {
            ps.setInt(1, updateId); ps.executeUpdate(); return true;
        } catch (SQLException ex) { return false; }
    }
}