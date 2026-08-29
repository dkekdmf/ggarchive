package com.example.archive;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.archive.storage.LocalPhotoStorage;
import com.example.archive.storage.PhotoStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** archive.storage.type 을 정하지 않으면 서버 디스크를 쓴다. */
@SpringBootTest
class LocalStorageSelectedTest {

	@Autowired
	private PhotoStorage storage;

	@Test
	void 설정이_없으면_서버_디스크를_쓴다() {
		assertThat(storage).isInstanceOf(LocalPhotoStorage.class);
	}
}
