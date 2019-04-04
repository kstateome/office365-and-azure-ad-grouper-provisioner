package edu.internet2.middleware.grouper.changeLog.consumer.model;

import com.squareup.moshi.Json;

import java.util.List;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GroupsOdata that = (GroupsOdata) o;
        return Objects.equals(context, that.context) &&
                Objects.equals(groups, that.groups) &&
                Objects.equals(nextPage, that.nextPage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(context, groups, nextPage);
    }
}
