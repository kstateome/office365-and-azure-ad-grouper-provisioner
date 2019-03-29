package edu.ksu.ome.o365.grouper;

import edu.internet2.middleware.grouper.changeLog.consumer.model.User;
import edu.internet2.middleware.subject.Subject;

import java.util.List;

public class MissingUserException extends Exception {
    private Subject subject;

    public MissingUserException(Subject subject) {
        super();
        this.subject = subject;
    }

    public Subject getSubject() {
        return subject;
    }
}
