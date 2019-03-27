package edu.internet2.middleware.grouper.changeLog.consumer.model;

import com.squareup.moshi.Json;

import java.util.List;

public class GroupsOdata {
    @Json(name = "@odata.context") public final String context;
    @Json(name = "value") public final List<Group> groups;
    @Json(name="@odata.nextLink") public  String nextPage;

    public GroupsOdata(String context, List<Group> groups) {
        this.context = context;
        this.groups = groups;
    }

    public GroupsOdata(String context, List<Group> groups, String nextPage) {
        this.context = context;
        this.groups = groups;
        this.nextPage = nextPage;
    }

    @Override
    public String toString() {
        return "GroupsOdata{" +
                "context='" + context + '\'' +
                ", groups=" + groups +
                ", nextPage='" + nextPage + '\'' +
                '}';
    }
}
