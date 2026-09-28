package com.dagachi.backend;

import com.dagachi.backend.testsupport.PostgresContainerTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Backend 전체 Spring ApplicationContext가
 * 테스트 전용 PostgreSQL + pgvector 환경에서 정상적으로 기동되는지 확인합니다.
 *
 * 실제 로컬 개발 DB를 사용하지 않고,
 * PostgresContainerTestBase에서 제공하는 Testcontainers DB를 사용합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests
		extends PostgresContainerTestBase {

	/**
	 * 별도의 비즈니스 로직이 아니라
	 * Spring Context 전체가 정상 초기화되는지 검증합니다.
	 *
	 * DataSource, Flyway, JPA Repository, Security 및
	 * 주요 Spring Bean 생성 여부를 함께 확인합니다.
	 */
	@Test
	void contextLoads() {
	}
}