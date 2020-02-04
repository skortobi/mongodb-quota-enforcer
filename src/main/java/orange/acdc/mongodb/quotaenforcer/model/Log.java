package orange.acdc.mongodb.quotaenforcer.model;

import java.util.Date;

public class Log {
    Date date;
    String str;
    String severity;

    public Log(String str, String type) {
        this.date = new Date();
        this.str = str;
        this.severity = type;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getStr() {
        return str;
    }

    public void setStr(String str) {
        this.str = str;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String type) {
        this.severity = type;
    }
}
