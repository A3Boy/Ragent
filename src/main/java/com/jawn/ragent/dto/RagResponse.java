package com.jawn.ragent.dto;

import java.util.List;

/**
 * RAG 响应对象 - 包含答案和引用来源
 */
public class RagResponse {
    private String answer;
    private List<Source> sources;
    private List<DebugInfo> debug;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<Source> getSources() {
        return sources;
    }

    public void setSources(List<Source> sources) {
        this.sources = sources;
    }

    public List<DebugInfo> getDebug() {
        return debug;
    }

    public void setDebug(List<DebugInfo> debug) {
        this.debug = debug;
    }

    /**
     * 引用来源片段
     */
    public static class Source {
        private String fileName;  // 文件名
        private String content;   // 内容片段（截断）
        private int snippetIndex; // 片段编号（从 1 开始）
        private List<String> urls; // 从 chunk 中提取的 URL 列表

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public int getSnippetIndex() {
            return snippetIndex;
        }

        public void setSnippetIndex(int snippetIndex) {
            this.snippetIndex = snippetIndex;
        }

        public List<String> getUrls() {
            return urls;
        }

        public void setUrls(List<String> urls) {
            this.urls = urls;
        }
    }

    /**
     * Debug 检索信息
     */
    public static class DebugInfo {
        private String text;   // 文本前100字
        private Double score;  // 相似度分数
        private boolean passed; // 是否通过阈值过滤

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
    }
}
