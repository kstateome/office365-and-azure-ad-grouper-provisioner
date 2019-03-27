package edu.ksu.ome.o365.grouper;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.Member;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.changeLog.consumer.Office365ApiClient;
import edu.internet2.middleware.grouper.changeLog.consumer.Office365ChangeLogConsumer;
import edu.internet2.middleware.grouper.changeLog.consumer.model.Members;
import edu.internet2.middleware.grouper.changeLog.consumer.model.User;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.subject.Subject;
import edu.internet2.middleware.subject.SubjectNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class O365GroupSync implements Runnable {
    private static final Log LOG = GrouperUtil.getLog(O365GroupSync.class);
    private Map<String, Object> debugMap;
    private Group grouperGroup;
    private int insertCount;
    private int deleteCount;
    private int unresolvableCount;
    private int totalCount;
    private Set<String> sourcesForSubjects;
    private String subjectAttributeForO365Username;

    private Office365ApiClient apiClient;

    public O365GroupSync(Map<String, Object> debugMap, Group grouperGroup, int insertCount, int deleteCount, int unresolvableCount, int totalCount, Set<String> sourcesForSubjects, String subjectAttributeForO365Username) {
        this.debugMap = debugMap;
        this.grouperGroup = grouperGroup;
        this.insertCount = insertCount;
        this.deleteCount = deleteCount;
        this.unresolvableCount = unresolvableCount;
        this.totalCount = totalCount;
        this.sourcesForSubjects = sourcesForSubjects;
        this.subjectAttributeForO365Username = subjectAttributeForO365Username;
         Office365ChangeLogConsumer temp = new Office365ChangeLogConsumer();
        apiClient = temp.getApiClient();
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

    public O365GroupSync invoke() {


        Members o365Members = apiClient.getMembersForGroup(grouperGroup);
        Set<String> usersInO365  = new TreeSet<>();
        for(User user : o365Members.users){
            usersInO365.add(user.userPrincipalName.split("@")[0]);
        }


        Set<String> grouperUsernamesInGroup = new HashSet<String>();

        //get usernames from grouper
        for (Member member : grouperGroup.getMembers()) {

            if (sourcesForSubjects.contains(member.getSubjectSourceId())) {
                if (StringUtils.equals("id", subjectAttributeForO365Username)) {
                    grouperUsernamesInGroup.add(member.getSubjectId());
                } else {
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
                        LOG.error("Cant find subject: " + member.getSubjectSourceId() + ": " +  member.getSubjectId());
                        //i guess continue
                    }
                }
            }
        }

        debugMap.put("grouperSubjectCount_" + grouperGroup.getName(), grouperUsernamesInGroup.size());
        totalCount += grouperUsernamesInGroup.size();

        //see which users are not in O365
        Set<String> grouperUsernamesNotInO365 = new TreeSet<String>(grouperUsernamesInGroup);
        grouperUsernamesNotInO365.removeAll(usersInO365);

        debugMap.put("additions_" + grouperGroup.getName(), grouperUsernamesNotInO365.size());

        //add to O365
        for (String grouperUsername : grouperUsernamesNotInO365) {
            Subject grouperSubject = SubjectFinder.findByIdentifier(grouperUsername,false);
            User user = apiClient.getUser(grouperSubject);

            if (user == null) {
                LOG.warn("User is not in o365: " + grouperUsername);
            } else {
                insertCount++;
                LOG.error("adding " + grouperSubject.getId()  +" to " + grouperGroup.getName());
                try {
                    apiClient.addMembership(grouperSubject,grouperGroup);
                } catch (MissingUserException e) {
                   LOG.warn(e.getSubject().getName() + " was not found in O365, skipping");
                }
            }
        }

        //see which users are not in O365
        Set<String> o365UsernamesNotInGrouper = new TreeSet<String>(usersInO365);
        o365UsernamesNotInGrouper.removeAll(grouperUsernamesInGroup);

        debugMap.put("removes_" + grouperGroup.getName(), o365UsernamesNotInGrouper.size());

        //remove from O365
        for (String o365username : o365UsernamesNotInGrouper) {
            Subject grouperSubject = SubjectFinder.findByIdentifier(o365username,false);
            LOG.error("removing " + grouperSubject.getId()  +" to " + grouperGroup.getName());

            try {
                apiClient.removeMembership(grouperSubject,grouperGroup);
            } catch (MissingUserException e) {
                LOG.warn(e.getSubject().getName() + " was not found in O365, skipping");
            }
            deleteCount++;
        }
        return this;
    }

    @Override
    public void run() {
        invoke();
    }
}
