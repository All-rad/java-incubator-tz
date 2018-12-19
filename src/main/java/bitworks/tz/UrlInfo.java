package bitworks.tz;

import java.util.Date;;

public class UrlInfo {
    public int id;
    public String url;
    public Date date;
    public int status;

    UrlInfo(int id, String url, Date date, int status) {
        this.id = id;
        this.url = url;
        this.date = date;
        this.status = status;
    }
    UrlInfo(){}
}
