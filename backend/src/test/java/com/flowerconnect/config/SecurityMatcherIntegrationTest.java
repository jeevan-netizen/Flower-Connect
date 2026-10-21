package com.flowerconnect.config;

import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for the production security chain's URL matchers
 * (plan task 5.5, the vendor slot-discovery route).
 *
 * <p>The slot rule used to place a double wildcard in the middle of the path:
 * the vendors segment, then a {@code **}, then the slots segment. Spring's
 * {@code PathPattern} syntax allows {@code **} only as the <em>last</em> element
 * of a pattern, so evaluating that rule threw {@code PatternParseException}.
 * The exception was raised inside the authorization filter, before the
 * dispatcher, and escaped {@code ExceptionTranslationFilter} — which handles
 * only access-denied and authentication exceptions — so it reached Tomcat's
 * error valve and the request was answered with a container-generated HTML 500
 * instead of the application's JSON error envelope.
 *
 * <p>That failure was invisible to the suite, and the blind spot is the point
 * of this class: the only test for the slot endpoint is a {@code @WebMvcTest}
 * slice, which supplies its own filter chain and never evaluates the
 * production matchers, and no integration test exercised the URL over HTTP. A
 * slice test cannot see this defect by construction — these two cases assert
 * what the <em>production</em> chain does with a request that reaches the rule.
 *
 * <p>Neither test touches the database. The security chain refuses the request
 * before any controller or repository is reached, so the assertions hold
 * whatever the shared singleton database happens to contain.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityMatcherIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * A request that reaches the slot rule is refused by the security chain
     * with the application's JSON envelope — a 401 — rather than by a
     * container-generated HTML 500.
     */
    @Test
    void anUnauthenticatedSlotRequestIsRefusedWithTheJsonEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/5/slots"))
                .andExpect(status().isUnauthorized())
                // A container error page is text/html; the application's
                // envelope is JSON. This is the assertion that fails when the
                // rule's pattern cannot be parsed.
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.path").value("/api/v1/vendors/5/slots"));
    }

    /**
     * A request that matches no rule at all falls through to
     * {@code anyRequest().authenticated()} and is refused with the same
     * envelope. Before the fix this was the shape of the failure: every
     * unmatched URL answered with a container-generated 500.
     */
    @Test
    void anUnauthenticatedRequestToAnUnmatchedRouteIsRefusedWithTheJsonEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/definitely-not-a-real-endpoint"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/definitely-not-a-real-endpoint"));
    }
}
