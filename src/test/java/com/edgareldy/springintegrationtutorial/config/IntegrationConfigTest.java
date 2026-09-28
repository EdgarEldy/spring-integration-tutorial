package com.edgareldy.springintegrationtutorial.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springintegrationtutorial.TestcontainersConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.test.context.ActiveProfiles;

/**
 * Checks the channel and poller beans declared by {@link IntegrationConfig} in a running context.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// Same annotations as the other context tests on purpose: Spring's test context cache then reuses one
// application context, and therefore one PostgreSQL container, for all of them.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class IntegrationConfigTest {

    @Autowired
    @Qualifier(IntegrationConfig.INTAKE_CHANNEL)
    private MessageChannel intakeChannel;

    @Autowired
    @Qualifier(IntegrationConfig.REVIEW_DECISION_CHANNEL)
    private MessageChannel reviewDecisionChannel;

    @Autowired
    @Qualifier(PollerMetadata.DEFAULT_POLLER)
    private PollerMetadata defaultPoller;

    @Test
    void _01_ShouldBeADirectChannel_WhenTheIntakeChannelIsInjected() {
        assertThat(intakeChannel).isInstanceOf(DirectChannel.class);
    }

    @Test
    void _02_ShouldPollWithAFixedDelayFromTheProperties_WhenTheDefaultPollerIsInjected() {
        // application-test.yml sets orders.poller.fixed-delay to 100 ms; max-messages-per-poll keeps its shared default.
        assertThat(defaultPoller.getTrigger())
                .isInstanceOfSatisfying(PeriodicTrigger.class, trigger -> {
                    assertThat(trigger.getPeriodDuration()).isEqualTo(Duration.ofMillis(100));
                    assertThat(trigger.isFixedRate()).isFalse();
                });
        assertThat(defaultPoller.getMaxMessagesPerPoll()).isEqualTo(10);
    }

    @Test
    void _03_ShouldBeADirectChannel_WhenTheReviewDecisionChannelIsInjected() {
        // The administrator's HTTP call must get the 404 or 422 of the decision step back, in its own thread.
        assertThat(reviewDecisionChannel).isInstanceOf(DirectChannel.class);
    }
}
