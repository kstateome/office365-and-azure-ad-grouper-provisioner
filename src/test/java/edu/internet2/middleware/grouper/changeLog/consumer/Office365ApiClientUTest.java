package edu.internet2.middleware.grouper.changeLog.consumer;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.attr.AttributeDefName;
import edu.internet2.middleware.grouper.changeLog.consumer.model.GroupsOdata;
import edu.internet2.middleware.grouper.changeLog.consumer.model.OAuthTokenInfo;
import edu.ksu.ome.o365.grouper.BufferedSourceMock;
import edu.ksu.ome.o365.grouper.UserLookupAcrossMultiplePotentialDomains;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class Office365ApiClientUTest {

    @Mock
    private RetrofitWrapper retrofit;
    @Mock
    private Office365AuthApiService office365AuthApiService;
    @Mock
    private GrouperSession grouperSession;

    @Mock
    private Call<OAuthTokenInfo> authTokenInfoCall;
    @Mock
    private OkHttpClient httpClient;
    @Mock
    private O365UserLookup o365UserLookup;
    @Mock
    ResponseWrapper responseWrapper;
    @Mock
    Office365GraphApiService office365GraphApiService;


    private Office365ApiClient apiClient;

    @Before
    public void setup(){
        MockitoAnnotations.initMocks(this);
        when(retrofit.create(Office365AuthApiService.class)).thenReturn(office365AuthApiService);
        when(retrofit.create(Office365GraphApiService.class)).thenReturn(office365GraphApiService);
        apiClient = new MockOffice365ApiClient("clientId","clientSecret","tenantId","scope",grouperSession);

    }

    @Test
    public void testGetTokenSuccess() throws Exception{
        /*
        String tokenType, String scope, int expiresIn, int expiresOn, int notBefore, String resource, String accessToken
         */
        OAuthTokenInfo tokenInfo = new OAuthTokenInfo("tokenType","scope",0,0,0,"resource","accessToken");
        retrofit2.Response<OAuthTokenInfo> response = Response.success(tokenInfo);
        when(office365AuthApiService.getOauth2Token("client_credentials","clientId","clientSecret","scope","https://graph.microsoft.com")).thenReturn(authTokenInfoCall);
         when(authTokenInfoCall.execute()).thenReturn(response);
         String result = apiClient.getToken();
         assertEquals("Token should be accessToken","accessToken",result);
    }
    @Test
    public void testGetTokenFailed() throws Exception {
        /*
        String tokenType, String scope, int expiresIn, int expiresOn, int notBefore, String resource, String accessToken
         */
        OAuthTokenInfo tokenInfo = new OAuthTokenInfo("tokenType", "scope", 0, 0, 0, "resource", "accessToken");
        retrofit2.Response<OAuthTokenInfo> response = Response.error(500, new ResponseBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("json");
            }

            @Override
            public long contentLength() {
                return 0;
            }

            @Override
            public BufferedSource source() {
                return new BufferedSourceMock();
            }
        });
        response.errorBody();
        when(office365AuthApiService.getOauth2Token("client_credentials", "clientId", "clientSecret", "scope", "https://graph.microsoft.com")).thenReturn(authTokenInfoCall);
        when(authTokenInfoCall.execute()).thenReturn(response);
        try {
            String result = apiClient.getToken();
            fail("This should have thrown an IO exception");
        } catch (IOException i) {
        } catch (Exception e){
            fail("this threw an unexpected exception of type " + e.getClass().getName() + ":" + e.getMessage());
        }


    }
    @Test
    public void testAddGroupNullValue(){
        apiClient.addGroup(null);
        verify(responseWrapper,never()).body();
    }
    @Test
    public void testAddGroup(){
        Group group = new Group();
        group.setNameDb("bob");
        group.setId("id");
        edu.internet2.middleware.grouper.changeLog.consumer.model.Group model =new edu.internet2.middleware.grouper.changeLog.consumer.model.Group(
                null,
                group.getName(),
                false,
                group.getUuid(),
                true,
                new ArrayList<String>(),
                group.getId()
        );
        when(responseWrapper.body()).thenReturn(model);
        apiClient.addGroup(group);
       verify(office365GraphApiService,times(1)).createGroup( model);
    }

    @Test
    public void testRemoveGroup(){
        String groupName = "bob";
        Map options = new TreeMap<>();
        options.put("$filter", "displayName eq '" + groupName + "'");
        edu.internet2.middleware.grouper.changeLog.consumer.model.Group model =new edu.internet2.middleware.grouper.changeLog.consumer.model.Group(
                "bob",
                "bob",
                false,
                null,
                true,
                new ArrayList<String>(),
                "bob"
        );
        List<edu.internet2.middleware.grouper.changeLog.consumer.model.Group> groupList = new LinkedList<>();
        groupList.add(model);
        GroupsOdata groupsOdata = new GroupsOdata("context",groupList);
        when(responseWrapper.body()).thenReturn(groupsOdata);
        apiClient.removeGroup("bob");
        verify(office365GraphApiService,times(1)).getGroups(options);
        verify(office365GraphApiService,times(1)).deleteGroup(model.id);

    }

    private class MockOffice365ApiClient extends Office365ApiClient {
        public MockOffice365ApiClient(String clientId, String clientSecret, String tenantId, String scope, GrouperSession grouperSession) {
            super(clientId, clientSecret, tenantId, scope, grouperSession);

        }


        @Override
        protected RetrofitWrapper buildRetroFit(HttpLoggingInterceptor loggingInterceptor) {
            return retrofit;
            // return null;
        }

        @Override
        protected OkHttpClient buildOkHttpClient(HttpLoggingInterceptor loggingInterceptor) {
            return httpClient;
        }

        @Override
        protected String getUserLookupClass() {
            return UserLookupAcrossMultiplePotentialDomains.class.getName();
        }

        @Override
        protected void buildO365UserLookupClass(String userLookupClass) {
            this.o365UserLookup = o365UserLookup;
        }

        @Override
        protected <T> ResponseWrapper<T> invokeResponse(retrofit2.Call<T> call) throws IOException {
            return responseWrapper;
        }
        @Override
        protected void addIdToGroupAttribute(Group group, ResponseWrapper response) {
            //do nothing.. Grouper code wants to start reading properties.
            //should probably assume grouper stuff works.
        }
    }
}
