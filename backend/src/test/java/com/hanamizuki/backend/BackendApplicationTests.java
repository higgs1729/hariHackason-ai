package com.hanamizuki.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the whole container, so it needs a database. The local profile
 * supplies an in-memory one; without it this fails on any machine that is not
 * currently running MySQL.
 */
@SpringBootTest
@ActiveProfiles({"local", "dev"})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
