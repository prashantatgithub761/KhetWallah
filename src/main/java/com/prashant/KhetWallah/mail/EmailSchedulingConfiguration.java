package com.prashant.KhetWallah.mail;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.email.scheduling-enabled", havingValue="true", matchIfMissing=true)
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class EmailSchedulingConfiguration {
}
