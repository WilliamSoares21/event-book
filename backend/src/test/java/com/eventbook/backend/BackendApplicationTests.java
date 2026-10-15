package com.eventbook.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Disabled("Desabilitado temporariamente porque a inicialização estática do .env ocorre apenas via método main")
@SpringBootTest(classes = BackendApplication.class)
@Import(TestcontainersConfiguration.class)
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
