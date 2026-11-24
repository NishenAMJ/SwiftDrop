package com.swiftdrop.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "swiftdrop.identity.outbox.publish-enabled=false")
class IdentityMerchantServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
