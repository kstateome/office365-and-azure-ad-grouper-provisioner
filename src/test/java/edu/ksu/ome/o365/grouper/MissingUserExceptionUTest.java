package edu.ksu.ome.o365.grouper;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.subj.GrouperSubject;
import edu.internet2.middleware.subject.Subject;
import edu.internet2.middleware.subject.provider.LdapSubject;
import edu.internet2.middleware.subject.provider.SubjectImpl;
import org.junit.Before;
import org.junit.Test;


import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MissingUserExceptionUTest {

    private MissingUserException missingUserException;
    private SubjectImpl subject;
    private List<String> accountList;

    @Before
    public void setup() {
        subject = new SubjectImpl("a", "b", "d", "e", "f");
        missingUserException = new MissingUserException(subject);
    }

    @Test
    public void testGetSubject() {
        assertEquals("should be the same object", subject, missingUserException.getSubject());
    }
}
