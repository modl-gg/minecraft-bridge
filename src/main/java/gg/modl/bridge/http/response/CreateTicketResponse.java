package gg.modl.bridge.http.response;

public class CreateTicketResponse {

    private boolean success;
    private String ticketId;
    private String message;

    public boolean isSuccess() {
        return success;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getMessage() {
        return message;
    }
}
