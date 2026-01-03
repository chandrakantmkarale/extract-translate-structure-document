package com.example.documentprocessor.service;

import com.example.documentprocessor.model.ProcessingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GoogleDriveService {

    public ProcessingResult downloadFile(String fileId) {
        return ProcessingResult.builder()
            .success(false)
            .error("Google Drive service not available")
            .stage("File Download")
            .build();
    }

    public ProcessingResult uploadFile(byte[] fileData, String fileName, String parentFolderId, String mimeType) {
        return ProcessingResult.builder()
            .success(false)
            .error("Google Drive service not available")
            .stage("File Upload")
            .build();
    }

    public ProcessingResult createDocument(String title, String parentFolderId) {
        return ProcessingResult.builder()
            .success(false)
            .error("Google Drive service not available")
            .stage("Document Creation")
            .build();
    }
}