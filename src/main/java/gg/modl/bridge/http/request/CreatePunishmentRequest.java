package gg.modl.bridge.http.request;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public class CreatePunishmentRequest {

    private final String targetUuid;
    private final String issuerName;
    @SerializedName("type_ordinal")
    private final int typeOrdinal;
    private final String reason;
    private final Long duration;
    private final Map<String, Object> data;
    private final List<String> notes;
    private final List<String> attachedTicketIds;
    private final String severity;
    private final String status;

    public CreatePunishmentRequest(String targetUuid, String issuerName, int typeOrdinal,
                                   String reason, Long duration, Map<String, Object> data,
                                   List<String> notes, List<String> attachedTicketIds,
                                   String severity, String status) {
        this.targetUuid = targetUuid;
        this.issuerName = issuerName;
        this.typeOrdinal = typeOrdinal;
        this.reason = reason;
        this.duration = duration;
        this.data = data;
        this.notes = notes;
        this.attachedTicketIds = attachedTicketIds;
        this.severity = severity;
        this.status = status;
    }

    public String getTargetUuid() {
        return targetUuid;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public int getTypeOrdinal() {
        return typeOrdinal;
    }

    public String getReason() {
        return reason;
    }

    public Long getDuration() {
        return duration;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public List<String> getNotes() {
        return notes;
    }

    public List<String> getAttachedTicketIds() {
        return attachedTicketIds;
    }

    public String getSeverity() {
        return severity;
    }

    public String getStatus() {
        return status;
    }
}
