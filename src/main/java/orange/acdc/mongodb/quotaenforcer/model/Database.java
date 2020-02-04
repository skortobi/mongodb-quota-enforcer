package orange.acdc.mongodb.quotaenforcer.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Database {

    String name;
    int size;
    String cfSpace;
    String cfName;
    String created_at;
    List<User> users;

    public Database(String name, int size) {
        this.name = name;
        this.size = size;
        users = new ArrayList<User>();

    }

    public Database(String name, int size, String cfName, String cfSpace, String created_at) {
        this.name = name;
        this.size = size;
        this.cfSpace = cfSpace;
        this.cfName = cfName;
        users = new ArrayList<User>();
        this.created_at = created_at;

    }

    public String toString(){
        return "Database : "+this.name+" , size : "+this.size;

    }

    public void addUser(User user){
        users.add(user);
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public int getSize() {
        return size;
    }

    public String getCfSpace() {
        return cfSpace;
    }

    public void setCfSpace(String cfSpace) {
        this.cfSpace = cfSpace;
    }

    public String getCfName() {
        return cfName;
    }

    public void setCfName(String cfName) {
        this.cfName = cfName;
    }
    public String getCreated_at(){
        return created_at;
    }

}
