package edu.ksu.ome.o365.grouper;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.Member;
import edu.internet2.middleware.grouper.changeLog.consumer.Office365ApiClient;
import edu.internet2.middleware.grouper.changeLog.consumer.Office365ChangeLogConsumer;
import edu.internet2.middleware.grouper.changeLog.consumer.model.MemberUser;
import edu.internet2.middleware.grouper.changeLog.consumer.model.Members;
import edu.internet2.middleware.grouper.changeLog.consumer.model.User;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class O365GroupSyncUTest {

    @Mock
    private Office365ApiClient apiClient;

    private Group grouperGroup;
    private O365GroupSync o365GroupSync;
    private Set<String> sourcesForSubjects;
    private String subjectAttributeForO365Username;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        grouperGroup = new Group();
        grouperGroup.setNameDb("testGroup");
        sourcesForSubjects = new HashSet<>();
        sourcesForSubjects.add("ldap");
        subjectAttributeForO365Username = "bob";
        o365GroupSync = new O365GroupSyncMock(new HashMap<String, Object>(), grouperGroup,
                0, 0, 0, 0, sourcesForSubjects, subjectAttributeForO365Username);

    }

    @Test
    public void testGetMembersForGroupFromO365() {
        edu.internet2.middleware.grouper.changeLog.consumer.model.Members members =
                new Members("context",new LinkedList<MemberUser>());
        MemberUser user = new MemberUser();
        user.setUserPrincipalName("bob@ksu.edu");
        user.setType("#microsoft.graph.user");
        members.users.add(user);
        when(apiClient.getMembersForGroup(grouperGroup)).thenReturn(members);
        Set<String> result = o365GroupSync.getMembersForGroupFromO365();
        assertTrue("set contains bob",result.contains("bob"));
        assertTrue("set is size 1",result.size() == 1);

    }
    @Test
    public void testRemoveUsersFromGroupsInO365 (){

    }

    private class O365GroupSyncMock extends O365GroupSync {
        public O365GroupSyncMock(Map<String, Object> debugMap, Group grouperGroup, int insertCount, int deleteCount, int unresolvableCount, int totalCount, Set<String> sourcesForSubjects, String subjectAttributeForO365Username) {
            super(debugMap, grouperGroup, insertCount, deleteCount, unresolvableCount, totalCount, sourcesForSubjects, subjectAttributeForO365Username);
        }
        @Override
        protected void setupApiClient() {
           setApiClient(apiClient);
        }
    }

}
