package com.dagachi.backend.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL + pgvector를 사용하는 Backend 통합 테스트의
 * 공통 Testcontainers 설정입니다.
 *
 * 전체 Gradle test 실행 동안 하나의 PostgreSQL 컨테이너를 공유합니다.
 *
 * Spring Test Context는 여러 테스트 클래스 사이에서 캐시될 수 있으므로,
 * 테스트 클래스마다 컨테이너를 시작/종료하면
 * 캐시된 DataSource가 이미 종료된 컨테이너의 JDBC URL을 계속 참조할 수 있습니다.
 *
 * 따라서 컨테이너를 JVM 동안 한 번만 시작하고,
 * 모든 통합 테스트가 동일한 JDBC URL을 사용하도록 유지합니다.
 */
public abstract class PostgresContainerTestBase {

    /**
     * Backend 통합 테스트 전체에서 공유하는 PostgreSQL + pgvector 컨테이너입니다.
     *
     * static 필드이므로 테스트 JVM에서 하나의 인스턴스만 사용합니다.
     */
    protected static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:pg17")
                            .asCompatibleSubstituteFor("postgres")
            )
                    .withDatabaseName("dagachi_test")
                    .withUsername("dagachi_test")
                    .withPassword("dagachi_test");

    /*
     * JUnit의 클래스별 @Container 생명주기에 맡기지 않고
     * 테스트 JVM에서 한 번만 시작합니다.
     *
     * 이렇게 해야 Spring Test Context 캐시가 DataSource를 재사용하더라도
     * 연결 대상 PostgreSQL 컨테이너가 중간에 종료되지 않습니다.
     */
    static {
        postgres.start();
    }

    /**
     * Spring Boot 테스트 DataSource가
     * 위의 공통 Testcontainers PostgreSQL을 사용하도록 등록합니다.
     */
    @DynamicPropertySource
    static void registerPostgresProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    /**
     * 테스트 인프라 설정만 제공하는 클래스이므로
     * 외부에서 직접 생성하지 않도록 protected 생성자를 사용합니다.
     */
    protected PostgresContainerTestBase() {
    }
}