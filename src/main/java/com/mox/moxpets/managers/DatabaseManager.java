package com.mox.moxpets.managers;

import com.mox.moxpets.MyPets;
import java.io.File;
import java.sql.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DatabaseManager {

    private final MyPets plugin;
    private Connection connection;

    public DatabaseManager(MyPets plugin) {
        this.plugin = plugin;
        connect();
        createTables();
    }

    private void connect() {
        try {
            File file = new File(plugin.getDataFolder(), "database.db");
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            plugin.getLogger().info("Veritabani baglantisi basarili (UTF-8 Destekli).");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createTables() {
        // disabled_buffs sütunu eklendi (TEXT olarak virgülle ayrılmış saklanacak)
        String sql = "CREATE TABLE IF NOT EXISTS moxpets_pet_data (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "pet_id VARCHAR(32) NOT NULL, " +
                "custom_name VARCHAR(64), " +
                "level INT DEFAULT 1, " +
                "exp DOUBLE DEFAULT 0, " +
                "trail VARCHAR(32), " +
                "armor_color INT, " +
                "glow BOOLEAN DEFAULT 0, " +
                "disabled_buffs TEXT, " +
                "PRIMARY KEY (uuid, pet_id)" +
                ");";

        String sqlPlayer = "CREATE TABLE IF NOT EXISTS moxpets_players (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "active_pet VARCHAR(32)" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            stmt.execute(sqlPlayer);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try { if (connection != null && !connection.isClosed()) connection.close(); }
        catch (SQLException e) { e.printStackTrace(); }
    }

    // KAYDETME (disabledBuffs setini String'e çeviriyoruz)
    public void savePetData(UUID uuid, String petId, String name, int level, double exp, String trail, Integer armorColor, boolean glow, Set<String> disabledBuffs) {
        String sql = "INSERT OR REPLACE INTO moxpets_pet_data (uuid, pet_id, custom_name, level, exp, trail, armor_color, glow, disabled_buffs) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, petId);
            ps.setString(3, name);
            ps.setInt(4, level);
            ps.setDouble(5, exp);
            ps.setString(6, trail);
            if (armorColor == null) ps.setNull(7, Types.INTEGER); else ps.setInt(7, armorColor);
            ps.setBoolean(8, glow);

            // Set -> String (Örn: "Hızlandırıcı,Tank")
            String buffsStr = (disabledBuffs == null || disabledBuffs.isEmpty()) ? "" : String.join(",", disabledBuffs);
            ps.setString(9, buffsStr);

            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // YÜKLEME (String'i Set'e çeviriyoruz)
    public PetManager.PetSaveData loadPetData(UUID uuid, String petId) {
        String sql = "SELECT * FROM moxpets_pet_data WHERE uuid = ? AND pet_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, petId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String name = rs.getString("custom_name");
                int level = rs.getInt("level");
                double exp = rs.getDouble("exp");
                String trail = rs.getString("trail");
                int armor = rs.getInt("armor_color");
                boolean hasArmor = !rs.wasNull();
                boolean glow = rs.getBoolean("glow");

                String buffsStr = rs.getString("disabled_buffs");
                Set<String> disabledBuffs = new HashSet<>();
                if (buffsStr != null && !buffsStr.isEmpty()) {
                    disabledBuffs.addAll(Arrays.asList(buffsStr.split(",")));
                }

                return new PetManager.PetSaveData(name, level, exp, trail, hasArmor ? armor : null, glow, disabledBuffs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveActivePet(UUID uuid, String petId) {
        String sql = "INSERT OR REPLACE INTO moxpets_players (uuid, active_pet) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, petId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public String loadActivePet(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT active_pet FROM moxpets_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("active_pet");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }
}