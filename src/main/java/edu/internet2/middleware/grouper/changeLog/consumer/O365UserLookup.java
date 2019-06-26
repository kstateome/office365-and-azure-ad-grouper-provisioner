package edu.internet2.middleware.grouper.changeLog.consumer;

import edu.internet2.middleware.grouper.changeLog.consumer.model.User;
import edu.internet2.middleware.subject.Subject;

import java.util.List;

public interface O365UserLookup {

    User getUserFromMs(Subject subject, String domain);
    void setApiClient(Office365ApiClient client);
}
