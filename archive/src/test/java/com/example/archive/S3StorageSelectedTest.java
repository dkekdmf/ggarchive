package com.example.archive;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.archive.storage.PhotoStorage;
import com.example.archive.storage.S3PhotoStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * archive.storage.type=s3 이면 S3 저장소가 골라진다.
 * 실제로 S3 에 접속하지는 않는다. 어떤 저장소가 준비되는지만 본다.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"archive.storage.type=s3",
		"cloud.aws.credentials.access-key=test-access-key",
		"cloud.aws.credentials.secret-key=test-secret-key",
		"cloud.aws.region.static=us-east-2",
		"cloud.aws.s3.bucket=test-bucket"
})
class S3StorageSelectedTest {

	@Autowired
	private PhotoStorage storage;

	@Test
	void s3_로_지정하면_S3_를_쓴다() {
		assertThat(storage).isInstanceOf(S3PhotoStorage.class);
	}
}
