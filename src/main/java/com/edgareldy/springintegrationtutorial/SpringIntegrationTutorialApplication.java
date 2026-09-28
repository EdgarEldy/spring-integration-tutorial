package com.edgareldy.springintegrationtutorial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the order-intake application: starts the REST layer and every Spring Integration flow.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// No @EnableIntegration here: spring-boot-starter-integration enables it through auto-configuration,
// which also registers the infrastructure every flow relies on (the default errorChannel, the
// taskScheduler that drives pollers, the integration graph support).
@SpringBootApplication
public class SpringIntegrationTutorialApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringIntegrationTutorialApplication.class, args);
	}

}
