package gg.modl.bridge.http.response;

public class PunishmentCreateResponse {

    private int status;
    private String message;
    private String punishmentId;

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getPunishmentId() {
        return punishmentId;
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
