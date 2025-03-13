package africa.siteanalysisagent.dto;

public record Setting(
        String defaultValue,
        String label,
        boolean required,
        String type) {

}
