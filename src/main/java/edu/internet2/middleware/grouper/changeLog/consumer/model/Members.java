package edu.internet2.middleware.grouper.changeLog.consumer.model;

import com.squareup.moshi.Json;

import java.util.List;

public class Members {
    @Json(name = "@odata.context") public final String context;
    @Json(name = "value") public final List<User> users;

    public Members(String context, List<User> users) {
        this.context = context;
        this.users = users;
    }

    @Override
    public String toString() {
        return "Members{" +
                "context='" + context + '\'' +
                ", users=" + users +
                '}';
    }
}
