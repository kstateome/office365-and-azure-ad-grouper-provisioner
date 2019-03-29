package edu.ksu.ome.o365.grouper;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.http.IHttpRequest;
import edu.internet2.middleware.grouper.changeLog.consumer.Office365ApiClient;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import org.apache.commons.logging.Log;

import java.io.IOException;

public class GraphServiceClientManager implements IAuthenticationProvider {
   private static final Log LOG = GrouperUtil.getLog(GraphServiceClientManager.class);
   private Office365ApiClient apiClient;
   //probably shouldn't mix and max,, but MS's docs are obtuse..

    @Override
    public void authenticateRequest(IHttpRequest request) {
        try {
            String token = apiClient.getToken();
            request.addHeader("Authorization", "Bearer " + token);
        } catch (IOException e) {
            LOG.error(e);
        }
    }

    public void setApiClient(Office365ApiClient apiClient) {
        this.apiClient = apiClient;
    }
}
