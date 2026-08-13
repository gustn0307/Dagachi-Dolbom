package com.dagachi.backend.domain.entity;
import com.dagachi.backend.common.entity.BaseCreatedEntity; import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="report_images") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ReportImage extends BaseCreatedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="report_id",nullable=false) private Report report;
 @Column(name="s3_key",nullable=false,unique=true,length=500) private String s3Key; @Column(name="original_filename",length=255) private String originalFilename;
 @Column(name="content_type",length=100) private String contentType; @Column(name="file_size") private Long fileSize;
}
