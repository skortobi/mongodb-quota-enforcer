package orange.acdc.mongodb.quotaenforcer.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import orange.acdc.mongodb.quotaenforcer.model.Database;
import org.cloudfoundry.client.CloudFoundryClient;

import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstancesRequest;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstancesResponse;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Controller
public class HttpController {

    @Value("${quota-enforcer.plansize}")
    int planSizeLimit;

    @Autowired
    CoreController coreController;

    @Autowired
    RestTemplate restTemplate;

    @GetMapping("/")
    public String indexPage(Model model){
        return homePage(model);
    }


    @GetMapping("/view")
    public String homePage(Model model){
        model.addAttribute("planSizeLimit", planSizeLimit);
        model.addAttribute("all_databases",getAllDatabases());
        model.addAttribute("quotaEnforcerLogs",coreController.getQuotaEnforcerLogs());
        return "view";
    }



    public List<Database> getAllDatabases(){
        List<Database> foundDatabases = new ArrayList<Database>();

        OAuth2AuthenticationDetails details = (OAuth2AuthenticationDetails) SecurityContextHolder.getContext().getAuthentication().getDetails();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(details.getTokenValue());
        HttpEntity<String> entity = new HttpEntity<String>(headers);
        System.out.println("        - Your Token : "+details.getTokenValue());
        JsonNode a = restTemplate.exchange("https://api.cloudfoundry.itn.intraorange/v2/service_instances", HttpMethod.GET, entity, JsonNode.class).getBody();
        for(JsonNode e : ((ArrayNode) a.get("resources"))) {



            if(e.get("entity").get("service_guid").textValue().equals("18a1f240-8eed-4717-a8bf-ae0006d8c5b7")) {
                System.out.println("            - Resource type guid: "+e.get("entity").get("service_guid").textValue());
                System.out.println("            - Resource name: "+ e.get("entity").get("name").textValue());
                Database db = new Database(e.get("metadata").get("guid").textValue(), coreController.getDatabaseSize(e.get("metadata").get("guid").textValue()), e.get("entity").get("name").textValue(), e.get("entity").get("space_guid").textValue(), e.get("metadata").get("created_at").textValue());
                foundDatabases.add(db);
            }
        }
        return foundDatabases;
    }


    public List<Database> auth(String username, String password) {
        List<Database> foundDatabases = new ArrayList<Database>();


        DefaultConnectionContext connectionContext = DefaultConnectionContext.builder()
                .apiHost("api.cloudfoundry.itn.intraorange")
                .skipSslValidation(true)
                .build();
        PasswordGrantTokenProvider tokenProvider = PasswordGrantTokenProvider.builder()
                .password(password)
                .username(username)
                .build();
        ReactorCloudFoundryClient cloudFoundryClient = ReactorCloudFoundryClient.builder()
                .connectionContext(connectionContext)
                .tokenProvider(tokenProvider)
                .build();
        Mono<ListServiceInstancesResponse> a = cloudFoundryClient.serviceInstances().list(ListServiceInstancesRequest.builder().build());
        a.block().getResources().forEach(e->{
            if(e.getEntity().getServiceId().equals("18a1f240-8eed-4717-a8bf-ae0006d8c5b7")){
                Database db = new Database(e.getMetadata().getId(),coreController.getDatabaseSize(e.getMetadata().getId()));
                foundDatabases.add(db);
            }});
        return foundDatabases;
    }
}
