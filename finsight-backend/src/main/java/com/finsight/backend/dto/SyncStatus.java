package com.finsight.backend.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayList;

public class SyncStatus {
    private String status; // IDLE, RUNNING, SUCCESS, ERROR
    private String stage; // SCANNING_FOLDERS, FETCHING_FILES, RUNNING_OCR, COMPLETED
    private int totalFiles;
    private int processedFiles;
    private int failedFiles;
    private int skippedFiles;
    private String message;
    private LocalDateTime lastSyncAt;
    private List<String> logs = new ArrayList<>();

    public SyncStatus() {
        this.status = "IDLE";
        this.stage = "";
    }

    // Getters and setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public int getTotalFiles() { return totalFiles; }
    public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
    public int getProcessedFiles() { return processedFiles; }
    public void setProcessedFiles(int processedFiles) { this.processedFiles = processedFiles; }
    public int getFailedFiles() { return failedFiles; }
    public void setFailedFiles(int failedFiles) { this.failedFiles = failedFiles; }
    public int getSkippedFiles() { return skippedFiles; }
    public void setSkippedFiles(int skippedFiles) { this.skippedFiles = skippedFiles; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public List<String> getLogs() { return logs; }
    public void setLogs(List<String> logs) { this.logs = logs; }
    public void addLog(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        this.logs.add("[" + timestamp + "] " + message);
    }
}
