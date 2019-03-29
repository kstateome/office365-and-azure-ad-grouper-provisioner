package edu.ksu.ome.o365.grouper;

import com.microsoft.graph.http.IHttpRequest;
import edu.internet2.middleware.grouper.changeLog.consumer.Office365ApiClient;
import jline.internal.TestAccessible;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GraphServiceClientManagerUTest {

    @Mock
    IHttpRequest httpRequest;

    @Mock
    Office365ApiClient apiClient;

    private GraphServiceClientManager graphServiceClientManager;

    @Before
    public void setup(){
        MockitoAnnotations.initMocks(this);
        graphServiceClientManager = new GraphServiceClientManager();
        graphServiceClientManager.setApiClient(apiClient);
    }
    @Test
    public void testAuthenticateRequest () throws Exception{

        when(apiClient.getToken()).thenReturn("bob");
        graphServiceClientManager.authenticateRequest(httpRequest);
        verify(httpRequest).addHeader("Authorization","Bearer " + "bob");


    }
}
