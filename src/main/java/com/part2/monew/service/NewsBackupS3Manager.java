package com.part2.monew.service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class NewsBackupS3Manager {

    private static final Logger logger = LoggerFactory.getLogger(NewsBackupS3Manager.class);
    private static final String BACKUP_FILE_PREFIX = "backups/news/";
    private static final String BACKUP_FILE_SUFFIX = ".json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final S3Client s3Client;
    private final String bucketName;

    public NewsBackupS3Manager(S3Client s3Client, @Qualifier("BucketName") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public String getBackupFileKey(LocalDate date) {
        return BACKUP_FILE_PREFIX + date.format(DATE_FORMATTER) + BACKUP_FILE_SUFFIX;
    }

    public void uploadNewsBackup(byte[] backupData, String s3Key) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType("application/json") 
                .build();
        try {
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(backupData));
        } catch (S3Exception e) {
            logger.error("S3 뉴스 백업 업로드 실패");
            throw new RuntimeException("S3 백업 업로드 실패: " + s3Key, e);
        }
    }

    public InputStream downloadNewsBackup(String s3Key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();
        try {
            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
            return s3Object;
        } catch (S3Exception e) {
            logger.error("S3 뉴스 백업 다운로드 실패");
            if (e.statusCode() == 404) {
                 logger.warn("S3에 해당 키의 백업 파일이 없습니다: {}", s3Key);
                 return null;
            }
            throw new RuntimeException("S3 백업 다운로드 실패: " + s3Key, e);
        }
    }

    public String getLatestBackupKey() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        LocalDate yesterday = today.minusDays(1);
        
        // 오늘 백업 파일이 있는지 먼저 확인
        String todayKey = getBackupFileKey(today);
        try {
            GetObjectRequest todayRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(todayKey)
                    .build();
            s3Client.getObject(todayRequest).close(); // 파일 존재 확인
            logger.info("오늘({}) 백업 파일 찾음: {}", today, todayKey);
            return todayKey;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                logger.info("오늘({}) 백업 파일이 없음. 어제({}) 백업 파일로 대체", today, yesterday);
                return getBackupFileKey(yesterday);
            } else {
                logger.error("오늘 백업 파일 확인 중 오류 발생: {}", e.getMessage());
                return getBackupFileKey(yesterday);
            }
        } catch (Exception e) {
            logger.error("오늘 백업 파일 확인 중 예상치 못한 오류: {}", e.getMessage());
            return getBackupFileKey(yesterday);
        }
    }
} 