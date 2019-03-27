package edu.ksu.ome.o365.grouper;

import edu.internet2.middleware.grouper.changeLog.consumer.model.User;
import edu.internet2.middleware.subject.Subject;

import java.util.List;

public class MissingUserException extends Exception {
    private Subject subject;
    private List<String> account;
    public MissingUserException(Subject subject, List<String> account) {
        super();
        this.subject = subject;
        this.account = account;
    }

    public Subject getSubject() {
        return subject;
    }
}
