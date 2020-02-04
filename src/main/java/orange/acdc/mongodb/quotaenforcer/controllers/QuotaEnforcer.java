package orange.acdc.mongodb.quotaenforcer.controllers;

import com.mongodb.*;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoDatabase;
import org.bson.BsonArray;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

import javax.management.relation.Role;
import java.util.*;

@Component
public class QuotaEnforcer {

    @Value("${quota-enforcer.plansize}")
    private float plansize;

    @Value("${spring.data.mongodb.host}")
    private String mongodbHost;

    @Value("${spring.data.mongodb.username}")
    private String mongoUsername;

    @Value("${spring.data.mongodb.password}")
    private String mongoPassword;

    @Value("${quota-enforcer.ignoreUsers}")
    private String[] usersToIgnore;
   
    @Autowired
    CoreController coreController;

    @Autowired
    MongoTemplate mongoTemplate;

    Set<String> violators = new HashSet<>();
    Set<String> reformers = new HashSet<>();
    Set<String> all_databases = new HashSet<>();


    private static final Logger logger = LoggerFactory.getLogger(QuotaEnforcer.class);


    public void getWriteUsers(String dbName){
        //Description: Get users with Write role in dbName
        logger.debug("getWriteUsers :: getting users with write role on database '"+ dbName + "' : \n ");
        coreController.addQuotaEnforcerLog("getWriteUsers :: getting users with write role on database '"+ dbName + "' : \n ","debug");
        //Prepare Query : db.system.users.find({roles:{role:"readWrite",db:"dbName"}, user : {$ne: "quota-enforcer"}})
        Query query = new Query();
        query.addCriteria(Criteria.where("db").is(dbName));
        query.addCriteria(Criteria.where("roles").elemMatch(Criteria.where("role").is("readWrite").and("db").is(dbName)));
        query.addCriteria(Criteria.where("user").nin(usersToIgnore));
 
	//Run Query and get users
        List<Document> users = mongoTemplate.find(query,Document.class,"system.users");
        logger.debug("getWriteUsers :: list of found users : "+ users.toString());
        coreController.addQuotaEnforcerLog("getWriteUsers :: list of found users : "+ users.toString(),"debug");
        users.forEach(user -> {
            logger.info("getWriteUsers :: user with write role on database  - username : "+user.getString("user") + " - roles : "+user.get("roles").toString());

            //Link
            limitPermissions(user.getString("user"),dbName);
        });

    }

    @Scheduled(fixedRate = 5000)
    public void getReadOnlyUsers(){
        logger.debug("getWriteUsers :: getting users with read only role");
        coreController.addQuotaEnforcerLog("getWriteUsers :: getting users with read only role","debug");
        Query query = new Query();
        query.addCriteria(Criteria.where("roles").elemMatch(Criteria.where("role").is("read")));
        query.addCriteria(Criteria.where("user").nin(usersToIgnore));
        query.fields().include("roles.$");
        query.fields().include("user");
        List<Document> users = mongoTemplate.find(query,Document.class,"system.users");

        users.forEach(user -> {
            List<Document> roles = (List<Document>) user.get("roles");
            roles.forEach(role -> {
                if(!violators.contains(role.getString("db"))) {
                    logger.info("quota-forced database : " + role.getString("db"));
                    violators.add(role.getString("db"));
                }

                //Link
                if(!checkSizeOnDisk(role.getString("db"))){
                    grantPermissions(user.getString("user"),role.getString("db"));
                }
            });
        });

    }

    @Scheduled(fixedRate = 5000)
    public void getDatabases(){
        //Description: Get databases with exceeded sizeOnDisk (except : admin, config, local)
        logger.debug("getDatabases :: getting databases with  sizeOnDisk >"+plansize +" Mo .");
        coreController.addQuotaEnforcerLog("getDatabases :: getting databases with  sizeOnDisk >"+plansize +" Mo .","debug");
        Document document = mongoTemplate.executeCommand("{ listDatabases: 1}");
        List<Document> all_docs = (List <Document>) document.get("databases");

        document = mongoTemplate.executeCommand("{ listDatabases: 1 , filter : {'name' : {$nin: ['admin', 'config', 'local']} , 'sizeOnDisk' : {$gt : "+plansize*1024*1024+"} }}");

        List <Document> databases =  (List <Document>) document.get("databases");
        if (databases.isEmpty() )
            logger.debug("getDatabases :: all databases sizes are < "+plansize+"Mo .");
            coreController.addQuotaEnforcerLog("getDatabases :: all databases sizes are < "+plansize+"Mo .","info");
       databases.forEach(e -> {
            if(!violators.contains(e.getString("name"))) {
                logger.info("getDatabases :: quota limit exceeded - name:  " + e.getString("name") + " - sizeOnDisk: " + (e.getDouble("sizeOnDisk") / 1024 / 1024) + "Mo .");
                violators.add(e.getString("name"));
            }
            //Link
            getWriteUsers(e.getString("name"));
        });
    }

    public boolean checkSizeOnDisk(String dbName){
        Document document = mongoTemplate.executeCommand("{ listDatabases: 1 , filter : {'name' : '"+dbName+"', 'sizeOnDisk' : {$gt : "+plansize*1024*1024+"} }}");
        if(document.getDouble("totalSize") == 0){
            logger.debug("checkSizeOnDisk :: database "+dbName+ " does not exceed size limit anymore.");
            return false;
        }
        return true;
    }

    public void grantPermissions(String username, String dbName){
        logger.info("grantPermissions  ::  grant write permission to "+username+ " on "+dbName);

        //Mongodb connector/credentials setting
        MongoCredential credential;
        credential = MongoCredential.createCredential(mongoUsername, "admin",
                mongoPassword.toCharArray());
        List<MongoCredential> credentials = new ArrayList<MongoCredential>();
        credentials.add(credential);
        ServerAddress serverAddress = new ServerAddress(mongodbHost, 27017);
        MongoClient mongo = new MongoClient(serverAddress,credentials);
        MongoDatabase database = mongo.getDatabase(dbName);

        //Grant write role
        BasicDBObject readRoleCommand = new BasicDBObject();
        BasicDBObject roleElement = new BasicDBObject();
        readRoleCommand.put("grantRolesToUser",username);
        roleElement.put("role","readWrite");
        roleElement.put("db",dbName);
        List<BasicDBObject> roles = new ArrayList<BasicDBObject>();
        roles.add(roleElement);
        readRoleCommand.put("roles",roles);
        Document doc =  database.runCommand(readRoleCommand);

        //Revoke read role
        BasicDBObject retriveWriteCommand = new BasicDBObject();
        retriveWriteCommand.put("revokeRolesFromUser",username);
        roleElement.put("role","read");
        roleElement.put("db",dbName);
        retriveWriteCommand.put("roles",roles);
        database.runCommand(retriveWriteCommand);
    }
    public void limitPermissions(String username, String dbName){
        logger.info("limitPermissions  ::  revoke write permission to "+username+ " on "+dbName);

        //Mongodb connector/credentials setting
        MongoCredential credential;
        credential = MongoCredential.createCredential(mongoUsername, "admin",
                mongoPassword.toCharArray());
        List<MongoCredential> credentials = new ArrayList<MongoCredential>();
        credentials.add(credential);
        ServerAddress serverAddress = new ServerAddress(mongodbHost, 27017);
        MongoClient mongo = new MongoClient(serverAddress,credentials);
        MongoDatabase database = mongo.getDatabase(dbName);

        //Grant read role
        BasicDBObject readRoleCommand = new BasicDBObject();
        BasicDBObject roleElement = new BasicDBObject();
        readRoleCommand.put("grantRolesToUser",username);
        roleElement.put("role","read");
        roleElement.put("db",dbName);
        List<BasicDBObject> roles = new ArrayList<BasicDBObject>();
        roles.add(roleElement);
        readRoleCommand.put("roles",roles);
        Document doc =  database.runCommand(readRoleCommand);

        //Revoke write role
        BasicDBObject retriveWriteCommand = new BasicDBObject();
        retriveWriteCommand.put("revokeRolesFromUser",username);
        roleElement.put("role","readWrite");
        roleElement.put("db",dbName);
        retriveWriteCommand.put("roles",roles);
        database.runCommand(retriveWriteCommand);
        //System.out.println(doc);

    }

    //find all read databases / recomparer la quota.
}
