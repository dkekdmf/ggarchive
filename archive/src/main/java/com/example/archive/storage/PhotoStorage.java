package com.example.archive.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사진 파일이 실제로 저장되는 곳. 지금은 서버 로컬 디스크를 쓰지만,
 * 나중에 S3 등으로 옮길 때 이 인터페이스만 새로 구현하면 나머지 코드는 그대로 둔다.
 */
public interface PhotoStorage {

	/** 파일을 저장하고, 나중에 다시 찾을 수 있는 키(상대 경로)를 돌려준다. */
	String store(MultipartFile file);

	Resource load(String storageKey);

	/** 파일을 지운다. 이미 없으면 조용히 넘어간다. */
	void delete(String storageKey);
}
