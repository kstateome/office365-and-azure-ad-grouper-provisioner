package edu.internet2.middleware.grouper.changeLog.consumer.model;

import java.util.Collection;

public class Group {
    public final String id;
    public final String displayName;
    public final boolean mailEnabled;
    public final String mailNickname;
    public final boolean securityEnabled;
    public final Collection<String> groupTypes;
    public final String description;

    public Group(String id, String displayName, boolean mailEnabled, String mailNickname, boolean securityEnabled, Collection<String> groupTypes, String description) {
        this.id = id;
        this.displayName = displayName;
        this.mailEnabled = mailEnabled;
        this.mailNickname = mailNickname;
        this.securityEnabled = securityEnabled;
        this.groupTypes = groupTypes;
        this.description = description;
    }

    @Override
    public String toString() {
        return "Group{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", mailEnabled=" + mailEnabled +
                ", mailNickname='" + mailNickname + '\'' +
                ", securityEnabled=" + securityEnabled +
                ", groupTypes=" + groupTypes +
                ", description='" + description + '\'' +
                '}';
    }
}
