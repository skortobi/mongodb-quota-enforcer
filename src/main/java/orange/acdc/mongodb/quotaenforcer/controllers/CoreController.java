package orange.acdc.mongodb.quotaenforcer.controllers;

import orange.acdc.mongodb.quotaenforcer.model.Database;
import orange.acdc.mongodb.quotaenforcer.model.Log;
import orange.acdc.mongodb.quotaenforcer.model.User;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Component
public class CoreController {
    @Autowired
    MongoTemplate mongoTemplate;

    LinkedList<Log> quotaEnforcerLogs;

    public CoreController() {
        quotaEnforcerLogs = new LinkedList<Log>();
    }

    public int getDatabaseSize(String dbName){
        System.out.println("Looking for db size of : "+dbName);
        Document document = mongoTemplate.executeCommand("{ listDatabases: 1, filter : {'name' : '"+dbName+"'}}");
        System.out.println(document.toJson());
        List<Document> db = ( List<Document>) document.get("databases");
        Double value =  db.get(0).getDouble("sizeOnDisk")/1024/1024;
	return value.intValue();
    }

    public List<Database> getAllDatabases(){
        List<Database> all_databases = new ArrayList<Database>();
        //Description: Get databases with exceeded sizeOnDisk (except : admin, config, local)
        Document document = mongoTemplate.executeCommand("{ listDatabases: 1}");
        List<Document> all_docs = (List <Document>) document.get("databases");
        all_docs.forEach(e -> {
                Double size = e.getDouble("sizeOnDisk")/1024/1024;
		Database db = new Database(e.getString("name"),size.intValue());
                Query query = new Query();
                query.addCriteria(Criteria.where("roles").elemMatch(Criteria.where("db").is(e.getString("name"))));
                query.addCriteria(Criteria.where("user").ne("quota-enforcer"));
                query.fields().include("roles.$");
                query.fields().include("user");
                List<Document> users = mongoTemplate.find(query,Document.class,"system.users");
                users.forEach(u -> {
                    List <Document> roles = (List<Document>) u.get("roles");
                    roles.forEach(r -> {
                        User user = new User(u.getString("user"),r.getString("role"), r.getString("db"));
                        db.addUser(user);
                    });
            });
            all_databases.add(db);
        });
        all_databases.forEach(db -> {System.out.println(db);});
        return all_databases;
    }


    public void addQuotaEnforcerLog(String log, String type){
        quotaEnforcerLogs.addFirst(new Log(log,type));
        if(quotaEnforcerLogs.size() > 250){
            quotaEnforcerLogs.removeLast();
        }
    }

    public List<Log> getQuotaEnforcerLogs(){
        return quotaEnforcerLogs;
    }
}
