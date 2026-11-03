package com.example.tvagent;

final class Whitelist {
    static String wlStatus(String shellOutput) {
        String status = shellOutput.trim();
        status = status.substring(status.lastIndexOf('\n') + 1).trim();
        return "keep".equals(status) || "set".equals(status) || "fail".equals(status)
                ? status : "unexpected";
    }
}
