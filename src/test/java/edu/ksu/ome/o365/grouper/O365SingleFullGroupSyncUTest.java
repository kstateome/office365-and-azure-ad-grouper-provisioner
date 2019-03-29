package edu.ksu.ome.o365.grouper;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.Member;
import edu.internet2.middleware.grouper.changeLog.consumer.Office365ApiClient;
import edu.internet2.middleware.grouper.changeLog.consumer.model.MemberUser;
import edu.internet2.middleware.grouper.changeLog.consumer.model.Members;
import edu.internet2.middleware.grouper.changeLog.consumer.model.User;
import edu.internet2.middleware.subject.Subject;
import edu.internet2.middleware.subject.SubjectNotFoundException;
import edu.internet2.middleware.subject.provider.LdapSubject;
import edu.internet2.middleware.subject.provider.SubjectImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class O365SingleFullGroupSyncUTest {

    @Mock
    private Office365ApiClient apiClient;
    @Mock
    private Subject mockSubject;
    @Mock
    private Member mockMember;

    private Group grouperGroup;
    private O365SingleFullGroupSync o365SingleFullGroupSync;
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
        when(mockMember.getSubject()).thenReturn(mockSubject);
        o365SingleFullGroupSync = new O365SingleFullGroupSyncMock(new HashMap<String, Object>(), grouperGroup,
                0, 0, 0, 0, sourcesForSubjects, subjectAttributeForO365Username);
    }

    @Test
    public void testGetMembersForGroupFromO365() {
        edu.internet2.middleware.grouper.changeLog.consumer.model.Members members =
                new Members("context", new LinkedList<MemberUser>());
        MemberUser user = new MemberUser();
        user.setUserPrincipalName("bob@ksu.edu");
        user.setType("#microsoft.graph.user");
        members.users.add(user);
        when(apiClient.getMembersForGroup(grouperGroup)).thenReturn(members);
        Set<String> result = o365SingleFullGroupSync.getMembersForGroupFromO365();
        assertTrue("set contains bob", result.contains("bob"));
        assertTrue("set is size 1", result.size() == 1);

    }

    @Test
    public void testRemoveUsersFromGroupsInO365() throws Exception {
        Set<String> testUsers = new HashSet<>();
        testUsers.add("bob");
        testUsers.add("cindy");
        o365SingleFullGroupSync.removeUsersFromGroupsInO365(testUsers);
        verify(apiClient, times(2)).removeMembership(any(Subject.class), any(Group.class));
        assertEquals("should have deleted 2", 2, o365SingleFullGroupSync.getDeleteCount());
    }

    @Test
    public void testAddUsersToGroupsInO365() throws Exception {
        Set<String> testUsers = new HashSet<>();
        testUsers.add("bob");
        testUsers.add("cindy");
        o365SingleFullGroupSync.addUsersToGroupsInO365(testUsers);
        verify(apiClient, times(2)).getUser(any(Subject.class), any(String.class));
    }

    @Test
    public void testAddUserToGroupsInO365NullUser() throws Exception {
        String username = "bob";
        Subject subject = o365SingleFullGroupSync.getSubjectByIdentifier(username);
        o365SingleFullGroupSync.addUserToGroupInO365(username, subject, null);
        assertEquals(0, o365SingleFullGroupSync.getInsertCount());
        verify(apiClient, never()).addMembership(subject, grouperGroup);

    }

    @Test
    public void testAddUserToGroupsInO365NonNullUser() throws Exception {
        String username = "bob";
        Subject subject = o365SingleFullGroupSync.getSubjectByIdentifier(username);
        User user = new User("id", false, "displayName", "id", "d", null, "bob@ksu.edu");
        o365SingleFullGroupSync.addUserToGroupInO365(username, subject, user);
        assertEquals(1, o365SingleFullGroupSync.getInsertCount());
        verify(apiClient, times(1)).addMembership(subject, grouperGroup);
    }

    @Test
    public void testLookupSubjectByAttributeBlankValue() {
        Set<String> testUsers = new HashSet<>();
        when(mockSubject.getAttributeValue(any(String.class))).thenReturn("");
        o365SingleFullGroupSync.lookupSubjectBySubjectAttribute(testUsers, mockMember);
        assertEquals("unresolvableCount should be 1", 1, o365SingleFullGroupSync.getUnresolvableCount());

    }
    @Test
    public void testLookupSubjectByAttributeValue() {
        Set<String> testUsers = new HashSet<>();
        String username = "bob";
        when(mockSubject.getAttributeValue(any(String.class))).thenReturn(username);
        o365SingleFullGroupSync.lookupSubjectBySubjectAttribute(testUsers, mockMember);
        assertEquals("unresolvableCount should be 0", 0, o365SingleFullGroupSync.getUnresolvableCount());
        assertTrue("testUsers should have bob",testUsers.contains(username));
    }
    @Test
    public void testLoogkupSubjectByAttributeSubjectNotFoundException() {
        Set<String> testUsers = new HashSet<>();
        String username = "bob";
        when(mockSubject.getAttributeValue(any(String.class))).thenThrow(new SubjectNotFoundException(""));
        o365SingleFullGroupSync.lookupSubjectBySubjectAttribute(testUsers, mockMember);
        assertEquals("unresolvableCount should be 1", 1, o365SingleFullGroupSync.getUnresolvableCount());
        assertTrue("testUsers should not have bob",!testUsers.contains(username));
    }

    @Test
    public void testLookupSubjectWithIdSet() {
        o365SingleFullGroupSync = new O365SingleFullGroupSyncMock(new HashMap<String, Object>(), grouperGroup,
                0, 0, 0, 0, sourcesForSubjects, "id");
        Set<String> testUsers = new HashSet<>();
        String username = "bob";
        when(mockMember.getSubjectId()).thenReturn(username);
        o365SingleFullGroupSync.lookupSubject(testUsers,mockMember);
        assertTrue("testUsers should have bob",testUsers.contains(username));
    }
    @Test
    public void testLookupSubjectWithIdNotSet() {
        Set<String> testUsers = new HashSet<>();
        String username = "bob";
        when(mockMember.getSubjectId()).thenReturn(username);
        o365SingleFullGroupSync.lookupSubject(testUsers,mockMember);
        assertFalse("testUsers should not have bob",testUsers.contains(username));
        assertEquals("unresolvableCount should be 1", 1, o365SingleFullGroupSync.getUnresolvableCount());
    }

    private class O365SingleFullGroupSyncMock extends O365SingleFullGroupSync {
        public O365SingleFullGroupSyncMock(Map<String, Object> debugMap, Group grouperGroup, int insertCount, int deleteCount, int unresolvableCount, int totalCount, Set<String> sourcesForSubjects, String subjectAttributeForO365Username) {
            super(debugMap, grouperGroup, insertCount, deleteCount, unresolvableCount, totalCount, sourcesForSubjects, subjectAttributeForO365Username);
        }

        @Override
        protected void setupApiClient() {
            setApiClient(apiClient);
        }

        @Override
        protected Subject getSubjectByIdentifier(String o365username) {
            SubjectImpl ldapSubject = new SubjectImpl(o365username, o365username, "description", "type", "ldap");
            return ldapSubject;
        }
    }

}
