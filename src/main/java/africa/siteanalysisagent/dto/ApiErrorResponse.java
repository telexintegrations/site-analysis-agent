package africa.siteanalysisagent.dto;

public record ApiErrorResponse(String message, String error, int status, String timestamp) {

}
