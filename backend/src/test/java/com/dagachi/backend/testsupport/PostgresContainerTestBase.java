package com.dagachi.backend.testsupport;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

public abstract class PostgresContainerTestBase {

    /**
     * Backend 통합 테스트에서 공통으로 사용할 PostgreSQL + pgvector 컨테이너입니다.
     *
     * Singleton Testcontainer
     * - 테스트 JVM에서 PostgreSQL 컨테이너를 한 번만 시작합니다.
     * - 여러 통합 테스트 클래스와 Spring ApplicationContext가
     *   동일한 PostgreSQL 컨테이너를 공유합니다.
     * - 특정 테스트 클래스 종료 시 컨테이너가 먼저 종료되지 않습니다.
     *
     * @ServiceConnection
     * - Spring Boot가 이 컨테이너의 JDBC URL, username, password를
     *   자동으로 DataSource에 연결합니다.
     *
     * 따라서 로컬 개발 DB나 운영 DB에 테스트 데이터가 저장되지 않습니다.
     */

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

    /*
     * 여러 Spring 통합 테스트 클래스가 동일한 ApplicationContext와
     * PostgreSQL Testcontainer를 안전하게 공유하도록
     * 테스트 JVM에서 컨테이너를 한 번만 시작합니다.
     *
     * JUnit @Container를 사용하지 않으므로
     * 특정 테스트 클래스가 끝났다고 컨테이너가 먼저 종료되지 않습니다.
     *
     * 테스트 JVM 종료 시에는 Testcontainers의 Ryuk이
     * 생성된 컨테이너를 정리합니다.
     */
    static {
        postgres.start();
    }

    /**
     * 테스트 인프라 설정만 제공하는 클래스이므로
     * 외부에서 직접 생성하지 않도록 protected 생성자를 사용합니다.
     */
    protected PostgresContainerTestBase() {
    }
}