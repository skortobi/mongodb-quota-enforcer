package orange.acdc.mongodb.quotaenforcer.model;

public class User {

    String name;
    String role;
    String database;

    public User(String name, String role, String db) {
        this.name = name;
        this.role = role;
        this.database = database;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }
}
