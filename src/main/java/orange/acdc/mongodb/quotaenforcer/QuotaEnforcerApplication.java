package orange.acdc.mongodb.quotaenforcer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuotaEnforcerApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuotaEnforcerApplication.class, args);
	}

}
