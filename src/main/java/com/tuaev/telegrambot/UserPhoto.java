package com.tuaev.telegrambot;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserPhoto {
    @Override
    public String toString() {
        return "{" +
                "ok=" + ok +
                ", result=" + result +
                '}';
    }

    private boolean ok;
    private Result result;

    public boolean isOk() {
        return ok;
    }

    public Result getResult() {
        return result;
    }

    public static class Result {
        @Override
        public String toString() {
            return "result{" +
                    "fileId='" + fileId + '\'' +
                    ", fileUniqueId='" + fileUniqueId + '\'' +
                    ", fileSize=" + fileSize +
                    ", filePath='" + filePath + '\'' +
                    '}';
        }

        @JsonProperty("file_id")
        private String fileId;
        @JsonProperty("file_unique_id")
        private String fileUniqueId;
        @JsonProperty("file_size")
        private int fileSize;
        @JsonProperty("file_path")
        private String filePath;

        public String getFileId() {
            return fileId;
        }

        public void setFileId(String fileId) {
            this.fileId = fileId;
        }

        public String getFileUniqueId() {
            return fileUniqueId;
        }

        public void setFileUniqueId(String fileUniqueId) {
            this.fileUniqueId = fileUniqueId;
        }

        public int getFileSize() {
            return fileSize;
        }

        public void setFileSize(int fileSize) {
            this.fileSize = fileSize;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
    }
}
