package com.taskpulse.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the whole application context.
 *
 * <p>Cheap to run and worth keeping: it is the only test that proves the security filter
 * chain, the JPA mappings, the springdoc configuration and the start-up initializer can all
 * be wired together at once. A broken bean definition fails here rather than at run time.</p>
 *
 * <p>The {@code test} profile points it at in-memory H2, so the suite neither needs a
 * running Postgres nor writes to the developer's own database.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class TaskpulseApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
