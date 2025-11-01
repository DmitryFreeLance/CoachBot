package com.example.coachbot;
import java.io.File;
import java.sql.*;

public class Db {

    // Путь к БД: System property "bot.db" -> ENV DB_PATH -> ./data/bot.db
    public static final String DB_PATH = firstNonNull(
            System.getProperty("bot.db"),
            System.getenv("DB_PATH"),
            "./data/bot.db"
    );

    private static String firstNonNull(String a, String b, String c) {
        return (a != null && !a.isBlank()) ? a : ((b != null && !b.isBlank()) ? b : c);
    }

    /** Подключение + безопасные PRAGMA + автосоздание каталога */
    public static Connection connect() throws SQLException {
        ensureParentDir(DB_PATH);
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("PRAGMA foreign_keys=ON");
        } catch (SQLException ignored) {}
        return c;
    }

    private static void ensureParentDir(String path) {
        File f = new File(path).getAbsoluteFile();
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
    }

    /** Главная инициализация: миграции -> создание недостающих таблиц */
    public static void init() throws Exception {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                migrateUsersTable(c);
                migrateGroupsTable(c);
                migrateSettingsTable(c); // ⬅ миграция settings(key,value) → settings(k,v)

                // Справочники/таблицы, которые ждут репозитории
                createIfMissing(c, """
                    CREATE TABLE IF NOT EXISTS settings(
                      k TEXT PRIMARY KEY,
                      v TEXT
                    )
                """);

                createIfMissing(c, """
                    CREATE TABLE IF NOT EXISTS user_states(
                      user_id TEXT PRIMARY KEY,
                      type    TEXT,
                      step    INTEGER,
                      payload TEXT
                    )
                """);

                createIfMissing(c, """
                    CREATE TABLE IF NOT EXISTS nutrition_plans(
                      user_id   TEXT NOT NULL,
                      date      TEXT NOT NULL,
                      calories  INTEGER,
                      proteins  REAL,
                      fats      REAL,
                      carbs     REAL,
                      set_by    TEXT,
                      PRIMARY KEY(user_id,date)
                    )
                """);

                createIfMissing(c, """
                    CREATE TABLE IF NOT EXISTS workout_plans(
                      user_id   TEXT NOT NULL,
                      date      TEXT NOT NULL,
                      text      TEXT,
                      set_by    TEXT,
                      PRIMARY KEY(user_id,date)
                    )
                """);

                createIfMissing(c, """
                    CREATE TABLE IF NOT EXISTS activity_norms(
                      user_id      TEXT NOT NULL,
                      date         TEXT NOT NULL,
                      water_liters REAL,
                      steps        INTEGER,
                      sleep_hours  REAL,
                      set_by       TEXT,
                      PRIMARY KEY(user_id,date)
                    )
                """);

                createIfMissing(c, """
                    CREATE TABLE IF NOT EXISTS reports(
                      user_id   TEXT NOT NULL,
                      date      TEXT NOT NULL,
                      sleep     REAL,
                      steps     INTEGER,
                      water     REAL,
                      kcal      INTEGER,
                      p         REAL,
                      f         REAL,
                      c         REAL,
                      note      TEXT,
                      photo_id  TEXT,
                      created_at INTEGER,
                      PRIMARY KEY(user_id,date)
                    )
                """);

                createIfMissing(c, """
                    CREATE TABLE IF NOT EXISTS processed_updates(
                      update_id INTEGER PRIMARY KEY
                    )
                """);

                createIfMissing(c, """
                    CREATE TABLE IF NOT EXISTS sent_notifications(
                      type    TEXT NOT NULL,
                      user_id TEXT NOT NULL,
                      date    TEXT NOT NULL,
                      PRIMARY KEY(type,user_id,date)
                    )
                """);

                createIfMissing(c, """
                    CREATE TABLE IF NOT EXISTS contacts(
                      admin_id TEXT PRIMARY KEY,
                      text     TEXT
                    )
                """);

                ensureDefaultSetting(c, "evening_time", "19:00");

                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /* ===================== Миграции ===================== */

    private static void migrateUsersTable(Connection c) throws SQLException {
        boolean exists = tableExists(c, "users");
        if (!exists) {
            try (Statement st = c.createStatement()) {
                st.execute("""
                CREATE TABLE users(
                  id         TEXT PRIMARY KEY,
                  username   TEXT,
                  first_name TEXT,
                  role       TEXT NOT NULL DEFAULT 'USER',
                  active     INTEGER NOT NULL DEFAULT 1
                )
            """);
            }
            return;
        }

        boolean hasId        = tableHasColumn(c, "users", "id");
        boolean hasUsername  = tableHasColumn(c, "users", "username");
        boolean hasFirstName = tableHasColumn(c, "users", "first_name");
        boolean hasRole      = tableHasColumn(c, "users", "role");
        boolean hasActive    = tableHasColumn(c, "users", "active");

        if (hasId && hasUsername && hasFirstName && hasRole && hasActive) return;

        boolean hasTgId   = tableHasColumn(c, "users", "tg_id");
        boolean hasUserId = tableHasColumn(c, "users", "user_id");
        String srcIdCol = hasId ? "id" : (hasTgId ? "tg_id" : (hasUserId ? "user_id" : null));

        try (Statement st = c.createStatement()) {
            st.execute("""
            CREATE TABLE users_new(
              id         TEXT PRIMARY KEY,
              username   TEXT,
              first_name TEXT,
              role       TEXT NOT NULL DEFAULT 'USER',
              active     INTEGER NOT NULL DEFAULT 1
            )
        """);

            if (srcIdCol != null) {
                String selId        = srcIdCol;
                String selUsername  = hasUsername  ? "username"   : "NULL";
                String selFirstName = hasFirstName ? "first_name" : "NULL";
                String selRole      = hasRole      ? "role"       : "'USER'";
                String selActive    = hasActive    ? "active"     : "1";

                String insert = "INSERT INTO users_new(id, username, first_name, role, active) " +
                        "SELECT " + selId + ", " + selUsername + ", " + selFirstName + ", " + selRole + ", " + selActive + " FROM users";
                st.execute(insert);
            }

            st.execute("DROP TABLE users");
            st.execute("ALTER TABLE users_new RENAME TO users");
        }
    }

    private static void migrateGroupsTable(Connection c) throws SQLException {
        boolean exists = tableExists(c, "groups");
        if (!exists) {
            try (Statement st = c.createStatement()) {
                st.execute("""
                    CREATE TABLE groups(
                      user_id  TEXT PRIMARY KEY,
                      admin_id TEXT NOT NULL
                    )
                """);
            }
            return;
        }

        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS groups_new(
                  user_id  TEXT PRIMARY KEY,
                  admin_id TEXT NOT NULL
                )
            """);
            if (tableHasColumn(c, "groups", "user_id") && tableHasColumn(c, "groups", "admin_id")) {
                st.execute("INSERT OR IGNORE INTO groups_new(user_id, admin_id) SELECT user_id, admin_id FROM groups");
            } else if (tableHasColumn(c, "groups", "uid") && tableHasColumn(c, "groups", "aid")) {
                st.execute("INSERT OR IGNORE INTO groups_new(user_id, admin_id) SELECT uid, aid FROM groups");
            }
            st.execute("DROP TABLE groups");
            st.execute("ALTER TABLE groups_new RENAME TO groups");
        }
    }

    // 🔧 миграция settings(key,value) → settings(k,v)
    private static void migrateSettingsTable(Connection c) throws SQLException {
        if (!tableExists(c, "settings")) {
            try (Statement st = c.createStatement()) {
                st.execute("""
                    CREATE TABLE settings(
                      k TEXT PRIMARY KEY,
                      v TEXT
                    )
                """);
            }
            return;
        }

        boolean hasK = tableHasColumn(c, "settings", "k");
        boolean hasV = tableHasColumn(c, "settings", "v");
        boolean hasKey = tableHasColumn(c, "settings", "key");
        boolean hasValue = tableHasColumn(c, "settings", "value");

        if (hasK && hasV && !hasKey && !hasValue) return;

        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS settings_new(
                  k TEXT PRIMARY KEY,
                  v TEXT
                )
            """);

            if (hasKey && hasValue) {
                st.execute("INSERT OR IGNORE INTO settings_new(k, v) SELECT key, value FROM settings");
            } else if (hasK && hasV) {
                st.execute("INSERT OR IGNORE INTO settings_new(k, v) SELECT k, v FROM settings");
            }

            st.execute("DROP TABLE settings");
            st.execute("ALTER TABLE settings_new RENAME TO settings");
        }
    }

    /* ===================== Вспомогательные ===================== */

    private static boolean tableExists(Connection c, String table) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception ignored) { return false; }
    }

    private static boolean tableHasColumn(Connection c, String table, String column) {
        try (PreparedStatement ps = c.prepareStatement("PRAGMA table_info(" + table + ")")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if (column.equalsIgnoreCase(name)) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void createIfMissing(Connection c, String createSql) throws SQLException {
        try (Statement st = c.createStatement()) { st.execute(createSql); }
    }

    private static void ensureDefaultSetting(Connection c, String key, String value) {
        try (PreparedStatement ps = c.prepareStatement("SELECT v FROM settings WHERE k=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        } catch (Exception ignored) {}
        try (PreparedStatement ins = c.prepareStatement("INSERT INTO settings(k,v) VALUES(?,?)")) {
            ins.setString(1, key);
            ins.setString(2, value);
            ins.executeUpdate();
        } catch (Exception ignored) {}
    }
}