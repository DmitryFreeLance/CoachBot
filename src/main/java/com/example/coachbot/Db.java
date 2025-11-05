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
                migrateSettingsTable(c);       // k/v -> key/value
                migrateReportsTable(c);        // полноценная схема reports
                migrateNormsTables(c);         // создаёт activity_norms и переносит из norms при наличии
                migrateUserParamsTable(c);     // ✅ МИГРАЦИЯ user_params (новая схема с chest_relaxed и т.д.)

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

                // Антидубли апдейтов (таблица, в которую пишет UpdatesRepo)
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

                // Таблица для состояний визардов
                createIfMissing(c, """
                    CREATE TABLE IF NOT EXISTS user_states(
                      user_id TEXT PRIMARY KEY,
                      type    TEXT,
                      step    INTEGER,
                      payload TEXT
                    )
                """);

                // Индекс на группы по администратору — полезен при росте данных
                createIfMissing(c, "CREATE INDEX IF NOT EXISTS idx_groups_admin ON groups(admin_id)");

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

    // settings: привести к key/value и перенести данные из старой схемы k/v
    private static void migrateSettingsTable(Connection c) throws SQLException {
        boolean exists = tableExists(c, "settings");
        if (!exists) {
            try (Statement st = c.createStatement()) {
                st.execute("""
                    CREATE TABLE settings(
                      key TEXT PRIMARY KEY,
                      value TEXT
                    )
                """);
            }
            return;
        }
        boolean hasKey = tableHasColumn(c, "settings", "key");
        boolean hasValue = tableHasColumn(c, "settings", "value");
        if (hasKey && hasValue) return;

        boolean hasK = tableHasColumn(c, "settings", "k");
        boolean hasV = tableHasColumn(c, "settings", "v");

        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE settings_new(
                  key TEXT PRIMARY KEY,
                  value TEXT
                )
            """);
            if (hasK && hasV) {
                st.execute("INSERT OR IGNORE INTO settings_new(key,value) SELECT k,v FROM settings");
            }
            st.execute("DROP TABLE settings");
            st.execute("ALTER TABLE settings_new RENAME TO settings");
        }
    }

    // reports: создать или пересобрать полную схему
    private static void migrateReportsTable(Connection c) throws SQLException {
        boolean exists = tableExists(c, "reports");
        if (!exists) {
            try (Statement st = c.createStatement()) {
                st.execute("""
                    CREATE TABLE reports(
                      user_id    TEXT NOT NULL,
                      date       TEXT NOT NULL,
                      sleep      REAL,
                      steps      INTEGER,
                      water      REAL,
                      kcal       INTEGER,
                      p          REAL,
                      f          REAL,
                      c          REAL,
                      note       TEXT,
                      photo_id   TEXT,
                      created_at INTEGER,
                      PRIMARY KEY(user_id,date)
                    )
                """);
            }
            return;
        }

        String[] required = {"user_id","date","sleep","steps","water","kcal","p","f","c","note","photo_id","created_at"};
        boolean ok = true;
        for (String col : required) if (!tableHasColumn(c, "reports", col)) { ok = false; break; }
        if (ok) return;

        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE reports_new(
                  user_id    TEXT NOT NULL,
                  date       TEXT NOT NULL,
                  sleep      REAL,
                  steps      INTEGER,
                  water      REAL,
                  kcal       INTEGER,
                  p          REAL,
                  f          REAL,
                  c          REAL,
                  note       TEXT,
                  photo_id   TEXT,
                  created_at INTEGER,
                  PRIMARY KEY(user_id,date)
                )
            """);

            String selSleep = tableHasColumn(c,"reports","sleep") ? "sleep" : "NULL";
            String selSteps = tableHasColumn(c,"reports","steps") ? "steps" : "NULL";
            String selWater = tableHasColumn(c,"reports","water") ? "water" : "NULL";
            String selKcal  = tableHasColumn(c,"reports","kcal")  ? "kcal"  : "NULL";
            String selP     = tableHasColumn(c,"reports","p")     ? "p"     : "NULL";
            String selF     = tableHasColumn(c,"reports","f")     ? "f"     : "NULL";
            String selC     = tableHasColumn(c,"reports","c")     ? "c"     : "NULL";
            String selNote  = tableHasColumn(c,"reports","note")  ? "note"  : "NULL";
            String selPhoto = tableHasColumn(c,"reports","photo_id") ? "photo_id" : "NULL";
            String selCreated = tableHasColumn(c,"reports","created_at") ? "created_at" : "NULL";

            String insert = "INSERT OR IGNORE INTO reports_new(user_id,date,sleep,steps,water,kcal,p,f,c,note,photo_id,created_at) " +
                    "SELECT user_id,date,"+selSleep+","+selSteps+","+selWater+","+selKcal+","+selP+","+selF+","+selC+","+selNote+","+selPhoto+","+selCreated+" FROM reports";
            st.execute(insert);

            st.execute("DROP TABLE reports");
            st.execute("ALTER TABLE reports_new RENAME TO reports");
        }
    }

    // нормы: целевая таблица activity_norms; перенос из старой norms, если была
    private static void migrateNormsTables(Connection c) throws SQLException {
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

        if (tableExists(c, "norms")) {
            boolean hasUser = tableHasColumn(c,"norms","user_id");
            boolean hasDate = tableHasColumn(c,"norms","date");
            boolean hasWater= tableHasColumn(c,"norms","water");
            boolean hasSteps= tableHasColumn(c,"norms","steps");
            boolean hasSleep= tableHasColumn(c,"norms","sleep");
            boolean hasBy   = tableHasColumn(c,"norms","set_by");
            if (hasUser && hasDate) {
                String w = hasWater? "water":"NULL";
                String s = hasSteps? "steps":"NULL";
                String sl= hasSleep? "sleep":"NULL";
                String by= hasBy? "set_by":"NULL";
                try (Statement st = c.createStatement()) {
                    st.execute("INSERT OR IGNORE INTO activity_norms(user_id,date,water_liters,steps,sleep_hours,set_by) " +
                            "SELECT user_id,date,"+w+","+s+","+sl+","+by+" FROM norms");
                } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Миграция таблицы user_params (используется визард «Мои параметры»).
     * Целевая схема:
     *   user_params(
     *     user_id TEXT PRIMARY KEY,
     *     weight REAL, waist REAL,
     *     chest_exhale REAL, chest_relaxed REAL, chest_inhale REAL,
     *     biceps_relaxed REAL, biceps_flex REAL,
     *     photo_id TEXT,
     *     updated_at INTEGER
     *   )
     *
     * Если таблица отсутствует — создаём.
     * Если присутствует, но нет нужных колонок — создаём user_params_new, копируем доступные поля, меняем.
     */
    private static void migrateUserParamsTable(Connection c) throws SQLException {
        boolean exists = tableExists(c, "user_params");
        if (!exists) {
            try (Statement st = c.createStatement()) {
                st.execute("""
                    CREATE TABLE user_params(
                      user_id TEXT PRIMARY KEY,
                      weight REAL,
                      waist REAL,
                      chest_exhale REAL,
                      chest_relaxed REAL,
                      chest_inhale REAL,
                      biceps_relaxed REAL,
                      biceps_flex REAL,
                      photo_id TEXT,
                      updated_at INTEGER
                    )
                """);
            }
            return;
        }

        String[] required = {
                "user_id","weight","waist",
                "chest_exhale","chest_relaxed","chest_inhale",
                "biceps_relaxed","biceps_flex",
                "photo_id","updated_at"
        };
        boolean ok = true;
        for (String col : required) if (!tableHasColumn(c, "user_params", col)) { ok = false; break; }
        if (ok) return;

        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE user_params_new(
                  user_id TEXT PRIMARY KEY,
                  weight REAL,
                  waist REAL,
                  chest_exhale REAL,
                  chest_relaxed REAL,
                  chest_inhale REAL,
                  biceps_relaxed REAL,
                  biceps_flex REAL,
                  photo_id TEXT,
                  updated_at INTEGER
                )
            """);

            // Подготовим селект с подстановкой NULL для отсутствующих колонок
            String selWeight = tableHasColumn(c,"user_params","weight") ? "weight" : "NULL";
            String selWaist  = tableHasColumn(c,"user_params","waist")  ? "waist"  : "NULL";

            String selChEx   = tableHasColumn(c,"user_params","chest_exhale")  ? "chest_exhale"  : "NULL";
            // во избежание несовпадений имён — пробуем возможные старые варианты
            String selChRl   =
                    tableHasColumn(c,"user_params","chest_relaxed")   ? "chest_relaxed" :
                            (tableHasColumn(c,"user_params","chest_relax")    ? "chest_relax" :
                                    tableHasColumn(c,"user_params","chest_rel")      ? "chest_rel" : "NULL");
            String selChIn   = tableHasColumn(c,"user_params","chest_inhale")  ? "chest_inhale"  : "NULL";

            String selBiRl   = tableHasColumn(c,"user_params","biceps_relaxed")? "biceps_relaxed": "NULL";
            String selBiFx   = tableHasColumn(c,"user_params","biceps_flex")   ? "biceps_flex"   : "NULL";

            String selPhoto  = tableHasColumn(c,"user_params","photo_id")      ? "photo_id"      : "NULL";
            String selUpd    = tableHasColumn(c,"user_params","updated_at")    ? "updated_at"    : "NULL";

            String insert = "INSERT OR IGNORE INTO user_params_new(" +
                    "user_id,weight,waist,chest_exhale,chest_relaxed,chest_inhale,biceps_relaxed,biceps_flex,photo_id,updated_at" +
                    ") SELECT user_id,"+selWeight+","+selWaist+","+selChEx+","+selChRl+","+selChIn+","+
                    selBiRl+","+selBiFx+","+selPhoto+","+selUpd+" FROM user_params";
            st.execute(insert);

            st.execute("DROP TABLE user_params");
            st.execute("ALTER TABLE user_params_new RENAME TO user_params");
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
        try (PreparedStatement ps = c.prepareStatement("SELECT value FROM settings WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        } catch (Exception ignored) {}
        try (PreparedStatement ins = c.prepareStatement("INSERT INTO settings(key,value) VALUES(?,?)")) {
            ins.setString(1, key);
            ins.setString(2, value);
            ins.executeUpdate();
        } catch (Exception ignored) {}
    }
}