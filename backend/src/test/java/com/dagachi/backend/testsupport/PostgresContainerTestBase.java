package com.dagachi.backend.testsupport;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL + pgvector를 사용하는 Backend 통합 테스트의 공통 Testcontainers 설정입니다.
 *
 * 각 테스트 클래스마다 PostgreSQLContainer 설정을 반복하지 않도록
 * 공통 베이스 클래스로 분리합니다.
 *
 * 개발/운영 환경과 동일하게 PostgreSQL 17 + pgvector 이미지를 사용하며,
 * 테스트 실행 시 별도의 임시 DB를 생성합니다.
 *
 * 이 클래스 자체는 테스트 메서드를 가지지 않고,
 * 실제 통합 테스트 클래스가 상속하여 사용합니다.
 */
@Testcontainers
public abstract class PostgresContainerTestBase {

    /**
     * Backend 통합 테스트에서 공통으로 사용할 PostgreSQL + pgvector 컨테이너입니다.
     *
     * @Container
     * - JUnit 테스트 실행 전에 컨테이너를 시작하고
     *   테스트 종료 시 Testcontainers가 자동으로 정리합니다.
     *
     * @ServiceConnection
     * - Spring Boot가 이 컨테이너의 JDBC URL, username, password를
     *   자동으로 DataSource에 연결합니다.
     *
     * 따라서 로컬 개발 DB나 운영 DB에 테스트 데이터가 저장되지 않습니다.
     */
    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:pg17")
                            // pgvector 이미지는 PostgreSQL 기반 이미지이므로
                            // PostgreSQLContainer와 호환되는 이미지임을 명시합니다.
                            .asCompatibleSubstituteFor("postgres")
            )
                    .withDatabaseName("dagachi_test")
                    .withUsername("dagachi_test")
                    .withPassword("dagachi_test");

    /**
     * 테스트 인프라 설정만 제공하는 클래스이므로
     * 외부에서 직접 생성하지 않도록 protected 생성자를 사용합니다.
     */
    protected PostgresContainerTestBase() {
    }
}