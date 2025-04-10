package africa.siteanalysisagent.dto;

public class Button {
    private String text;
    private String value;
    private String action;

    public Button(String text, String value, String action){
        this.text = text;
        this.value = value;
        this.action = action;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
