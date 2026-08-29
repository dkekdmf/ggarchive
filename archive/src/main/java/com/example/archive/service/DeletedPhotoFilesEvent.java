package com.example.archive.service;

import java.util.List;

/**
 * 사진 기록이 지워진 뒤 발행된다. 실제 파일은 이 사건을 받아 지운다.
 *
 * <p>트랜잭션이 되돌아갔는데 파일만 먼저 지워지는 일을 막으려고,
 * 커밋이 끝난 뒤에 지우도록 분리했다.
 */
public record DeletedPhotoFilesEvent(List<String> storageKeys) {
}
