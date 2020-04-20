package edu.internet2.middleware.grouper.changeLog.consumer.model;

import java.util.Collection;
import java.util.Objects;

public class Group {
    public enum Visibility {Public, Private, Hiddenmembership}

    public final String id;
    public final String displayName;
    public final boolean mailEnabled;
    public final String mailNickname;
    public final boolean securityEnabled;
    public final Collection<String> groupTypes;
    public final String description;
    public final Visibility visibility;

    public Group(String id, String displayName, boolean mailEnabled, String mailNickname, boolean securityEnabled, Collection<String> groupTypes, String description,Visibility visibility) {
        this.id = id;
        this.displayName = displayName;
        this.mailEnabled = mailEnabled;
        this.mailNickname = mailNickname;
        this.securityEnabled = securityEnabled;
        this.groupTypes = groupTypes;
        this.description = description;
        this.visibility = visibility;
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
                ", visibility=" + visibility +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Group group = (Group) o;
        return mailEnabled == group.mailEnabled &&
                securityEnabled == group.securityEnabled &&
                Objects.equals(id, group.id) &&
                Objects.equals(displayName, group.displayName) &&
                Objects.equals(mailNickname, group.mailNickname) &&
                Objects.equals(groupTypes, group.groupTypes) &&
                Objects.equals(description, group.description) &&
                visibility == group.visibility;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, mailEnabled, mailNickname, securityEnabled, groupTypes, description, visibility);
    }
}
