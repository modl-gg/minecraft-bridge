package gg.modl.bridge.http.request;

import java.util.List;

public class CreateTicketRequest {

    private final String creatorUuid;
    private final String creatorName;
    private final String type;
    private final String subject;
    private final String description;
    private final String reportedPlayerUuid;
    private final String reportedPlayerName;
    private final List<String> tags;
    private final String priority;
    private final String createdServer;

    public CreateTicketRequest(String creatorUuid, String creatorName, String type,
                               String subject, String description,
                               String reportedPlayerUuid, String reportedPlayerName,
                               List<String> tags, String priority, String createdServer) {
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.type = type;
        this.subject = subject;
        this.description = description;
        this.reportedPlayerUuid = reportedPlayerUuid;
        this.reportedPlayerName = reportedPlayerName;
        this.tags = tags;
        this.priority = priority;
        this.createdServer = createdServer;
    }

    public String getCreatorUuid() {
        return creatorUuid;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public String getType() {
        return type;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public String getReportedPlayerUuid() {
        return reportedPlayerUuid;
    }

    public String getReportedPlayerName() {
        return reportedPlayerName;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getPriority() {
        return priority;
    }

    public String getCreatedServer() {
        return createdServer;
    }
}
