package edu.ksu.ome.o365.grouper;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.Member;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.changeLog.consumer.Office365ApiClient;
import edu.internet2.middleware.grouper.changeLog.consumer.Office365ChangeLogConsumer;
import edu.internet2.middleware.grouper.changeLog.consumer.model.MemberUser;
import edu.internet2.middleware.grouper.changeLog.consumer.model.Members;
import edu.internet2.middleware.grouper.changeLog.consumer.model.User;
import edu.internet2.middleware.subject.Subject;
import edu.internet2.middleware.subject.SubjectNotFoundException;
import edu.internet2.middleware.subject.provider.LdapSubject;
import edu.internet2.middleware.subject.provider.SubjectImpl;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class O365SingleFullGroupSync implements Runnable {
    private static final Log LOG = LogFactory.getLog(O365SingleFullGroupSync.class);
    private Map<String, Object> debugMap;
    private Group grouperGroup;
    private int insertCount;
    private int deleteCount;
    private int unresolvableCount;
    private int totalCount;
    private Set<String> sourcesForSubjects;
    private String subjectAttributeForO365Username;
    private String tenantId;

    private Office365ApiClient apiClient;

    public O365SingleFullGroupSync(Map<String, Object> debugMap, Group grouperGroup, int insertCount, int deleteCount, int unresolvableCount, int totalCount, Set<String> sourcesForSubjects, String subjectAttributeForO365Username) {
        this.debugMap = debugMap;
        this.grouperGroup = grouperGroup;
        this.insertCount = insertCount;
        this.deleteCount = deleteCount;
        this.unresolvableCount = unresolvableCount;
        this.totalCount = totalCount;
        this.sourcesForSubjects = sourcesForSubjects;
        this.subjectAttributeForO365Username = subjectAttributeForO365Username;
        setupApiClient();
    }

    protected void setupApiClient() {
        Office365ChangeLogConsumer temp = new Office365ChangeLogConsumer();
        apiClient = temp.getApiClient();
        tenantId = temp.getTenantId();
    }


    public int getInsertCount() {
        return insertCount;
    }

    public int getDeleteCount() {
        return deleteCount;
    }

    public int getUnresolvableCount() {
        return unresolvableCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public O365SingleFullGroupSync invoke() {
        GrouperSession.startRootSessionIfNotStarted();
        // update group data..
        apiClient.updateGroup(grouperGroup);
        // update members..
        Set<String> usersInO365 = getMembersForGroupFromO365();
        Set<String> grouperUsernamesInGroup = new HashSet<String>();
        //get usernames from grouper
        getUsernamesFromGrouper(grouperUsernamesInGroup);
        debugMap.put("grouperSubjectCount_" + grouperGroup.getName(), grouperUsernamesInGroup.size());
        totalCount += grouperUsernamesInGroup.size();
        //see which users are not in O365
        Set<String> grouperUsernamesNotInO365 = new TreeSet<String>(grouperUsernamesInGroup);
        grouperUsernamesNotInO365.removeAll(usersInO365);
        debugMap.put("additions_" + grouperGroup.getName(), grouperUsernamesNotInO365.size());
        //add to O365
        addUsersToGroupsInO365(grouperUsernamesNotInO365);
        //see which users are not in O365
        Set<String> o365UsernamesNotInGrouper = new TreeSet<String>(usersInO365);
        o365UsernamesNotInGrouper.removeAll(grouperUsernamesInGroup);
        debugMap.put("removes_" + grouperGroup.getName(), o365UsernamesNotInGrouper.size());
        //remove from O365
        removeUsersFromGroupsInO365(o365UsernamesNotInGrouper);

        return this;
    }

    void setApiClient(Office365ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    Set<String> getMembersForGroupFromO365() {
        Members o365Members = apiClient.getMembersForGroup(grouperGroup);
        Set<String> usersInO365 = new TreeSet<>();
        for (MemberUser user : o365Members.users) {
            if(user.getType().equals("#microsoft.graph.user")) {
                usersInO365.add(user.getUserPrincipalName().split("@")[0]);
            }
        }
        return usersInO365;
    }

    void removeUsersFromGroupsInO365(Set<String> o365UsernamesNotInGrouper) {
        for (String o365username : o365UsernamesNotInGrouper) {
            Subject grouperSubject = getSubjectByIdentifier(o365username);
            if(grouperSubject != null ) {
                LOG.info("removing " + grouperSubject.getId() + " to " + grouperGroup.getName());

                try {
                    apiClient.removeMembership(grouperSubject, grouperGroup);
                } catch (MissingUserException e) {
                    LOG.warn(e.getSubject().getName() + " was not found in O365, skipping");
                }
                deleteCount++;
            }
        }
    }

    protected Subject getSubjectByIdentifier(String o365username) {
        return SubjectFinder.findByIdentifier(o365username, false);
    }

    void addUsersToGroupsInO365(Set<String> grouperUsernamesNotInO365) {
        for (String grouperUsername : grouperUsernamesNotInO365) {
            Subject grouperSubject = getSubjectByIdentifier(grouperUsername);
            if(grouperSubject != null) {
                User user = apiClient.getUser(grouperSubject, this.tenantId);
                addUserToGroupInO365(grouperUsername, grouperSubject, user);
            }
        }
    }

    void addUserToGroupInO365(String grouperUsername, Subject grouperSubject, User user) {
        if (user == null) {
            LOG.warn("User is not in o365: " + grouperUsername);
        } else {
            insertCount++;
            LOG.info("adding " + grouperSubject.getId() + " to " + grouperGroup.getName());
            try {
                apiClient.addMembership(grouperSubject, grouperGroup);
            } catch (MissingUserException e) {
                LOG.warn(e.getSubject().getName() + " was not found in O365, skipping");
            }
        }
    }

    void getUsernamesFromGrouper(Set<String> grouperUsernamesInGroup) {
        for (Member member : grouperGroup.getMembers()) {
            if (sourcesForSubjects.contains(member.getSubjectSourceId())) {
                lookupSubject(grouperUsernamesInGroup, member);
            }
        }
    }

    void lookupSubject(Set<String> grouperUsernamesInGroup, Member member) {
        if (StringUtils.equals("id", subjectAttributeForO365Username)) {
            grouperUsernamesInGroup.add(member.getSubjectId());
        } else {
            lookupSubjectBySubjectAttribute(grouperUsernamesInGroup, member);
        }
    }

    void lookupSubjectBySubjectAttribute(Set<String> grouperUsernamesInGroup, Member member) {
        try {
            Subject subject = member.getSubject();
            String attributeValue = subject.getAttributeValue(subjectAttributeForO365Username);
            if (StringUtils.isBlank(attributeValue)) {
                //i guess this is ok
                LOG.info("Subject has a blank: " + subjectAttributeForO365Username + ", " + member.getSubjectSourceId() + ", " + member.getSubjectId());
                unresolvableCount++;
            } else {
                grouperUsernamesInGroup.add(attributeValue);
            }
        } catch (SubjectNotFoundException snfe) {
            unresolvableCount++;
            LOG.error("Cant find subject: " + member.getSubjectSourceId() + ": " + member.getSubjectId());
            //i guess continue
        }
    }

    @Override
    public void run() {
        invoke();
        Office365ChangeLogConsumer.lastScheduledMap.remove(this.grouperGroup.getName());
    }
}
