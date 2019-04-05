package edu.internet2.middleware.grouper.changeLog.consumer;

import edu.internet2.middleware.grouper.exception.MemberAddAlreadyExistsException;
import edu.internet2.middleware.grouper.exception.MemberDeleteAlreadyDeletedException;
import org.apache.log4j.Logger;

import java.io.IOException;

final class RetroFitInvoker<T> {
    private static final Logger logger = Logger.getLogger(RetroFitInvoker.class);
    private Office365ApiClient office365ApiClient;
    private retrofit2.Call<T> call;
    private boolean isMembershipRemove;

    public RetroFitInvoker(Office365ApiClient office365ApiClient, retrofit2.Call<T> call,boolean isMembershipRemove) {
        this.office365ApiClient = office365ApiClient;
        this.call = call;
        this.isMembershipRemove = isMembershipRemove;
    }

    public final ResponseWrapper<T> invoke() throws IOException {
        for (int retryMax = 2; retryMax > 0; retryMax--) {
            if (office365ApiClient.token == null) {
                office365ApiClient.token = office365ApiClient.getToken();
            }

                if(!call.isExecuted()) {
                    retrofit2.Response<T> r = call.execute();

                    if (r.isSuccessful()) {
                        return new ResponseWrapper<T>(r);
                    } else {
                        processErrorResponse(r);
                    }
                }

        }
        throw new IOException("Retry failed for: " + call.request().url());
    }

    protected void processErrorResponse(retrofit2.Response<T> r) throws IOException {
        switch (r.code()) {
            case 401:
                cloneCallToPreventReuse();
                break;
            case 400:
                checkIfMemberAlreadyExistsElseThrowException(r);
                return;
            case 404:
                checkIfMemberIsAlreadyDeletedElseThrowExcption(r);
                return;
            default:
                throw new IOException("Unhandled invoke response (" + r.code() + ") " + r.errorBody().string());
        }
    }

    private void checkIfMemberIsAlreadyDeletedElseThrowExcption(retrofit2.Response<T> r) throws IOException {
        if(isMembershipRemove){
            throw new MemberDeleteAlreadyDeletedException("member is already a deleted from the group in O365");
        } else {
            throw new IOException("Unhandled invoke response (" + r.code() + ") " + r.errorBody().string());
        }
    }

    private void checkIfMemberAlreadyExistsElseThrowException(retrofit2.Response<T> r) throws IOException {
        if (r.message().contains("One or more added object references already exist") ||  r.errorBody().string().contains("One or more added object references already exist")) {
            // this was an add, but the user already existed..
            throw new MemberAddAlreadyExistsException("member is already a member of the group in O365");
        } else {
            throw new IOException("Unhandled invoke response (" + r.code() + ") " + r.errorBody().string());
        }
    }

    private void cloneCallToPreventReuse() {
        logger.debug("auth fail, retry: " + call.request().url());
        // Call objects cannot be reused, so docs say to use clone() to create a new one with the
        // same specs for retry purposes
        call = call.clone();
        // null out existing token so we'll fetch a new one on next loop pass
    }
}
