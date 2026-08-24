package com.dagachi.backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    // DefaultCredentialsProvider
    //        ↓
    // 환경에 맞는 AWS 인증 정보 탐색
    //        ↓
    // 로컬
    // AWS_ACCESS_KEY_ID
    // AWS_SECRET_ACCESS_KEY
    //        ↓
    // 나중에 AWS 서버
    // IAM Role 자격증명
    @Bean
    public S3Client s3Client(
            @Value("${aws.region}") String region
    ) {
        return S3Client.builder()
                .region(Region.of(region))
                // 로컬에서는 환경변수, 배포 환경에서는 IAM Role 등
                // AWS 기본 자격증명 체인에서 인증 정보를 찾는다.
                .credentialsProvider(
                        DefaultCredentialsProvider.builder().build()
                )
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(
            @Value("${aws.region}") String region
    ) {
        return S3Presigner.builder()
                .region(Region.of(region))
                // S3Client와 동일한 AWS 기본 자격증명 체인을 사용한다.
                .credentialsProvider(
                        DefaultCredentialsProvider.builder().build()
                )
                .build();
    }
}