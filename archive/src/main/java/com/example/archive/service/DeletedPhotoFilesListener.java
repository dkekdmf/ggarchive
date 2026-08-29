package com.example.archive.service;

import com.example.archive.storage.PhotoStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DeletedPhotoFilesListener {

	private static final Logger log = LoggerFactory.getLogger(DeletedPhotoFilesListener.class);

	private final PhotoStorage photoStorage;

	public DeletedPhotoFilesListener(PhotoStorage photoStorage) {
		this.photoStorage = photoStorage;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onDeleted(DeletedPhotoFilesEvent event) {
		event.storageKeys().forEach(photoStorage::delete);
		log.info("사진 파일 {}개를 지웠습니다.", event.storageKeys().size());
	}
}
