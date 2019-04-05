package edu.internet2.middleware.grouper.changeLog.consumer;

import com.google.gson.Gson;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.requests.extensions.GraphServiceClient;
import com.microsoft.graph.requests.extensions.IDirectoryObjectCollectionWithReferencesPage;
import com.microsoft.graph.requests.extensions.IGroupCollectionPage;
import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.attr.AttributeDefName;
import edu.internet2.middleware.grouper.attr.finder.AttributeDefNameFinder;
import edu.internet2.middleware.grouper.changeLog.consumer.model.*;
import edu.internet2.middleware.grouper.exception.MemberAddAlreadyExistsException;
import edu.internet2.middleware.grouper.exception.MemberDeleteAlreadyDeletedException;
import edu.internet2.middleware.subject.Subject;
import edu.ksu.ome.o365.grouper.GraphServiceClientManager;
import edu.ksu.ome.o365.grouper.MissingUserException;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.log4j.Logger;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;

import java.io.IOException;
import java.util.*;

/**
 * This class interacts with the Microsoft Graph API.
 * Their Java client leaves much to be desired, however it handles pagination better than the 'Rest' version of the client.
 * So, pagination is being handled through the IGraphServiceClient,(getting members and list of all groups), while
 * individual stuff is using retrofit to call the rest API.
 */
public class Office365ApiClient implements O365UserLookup {
    private static final Logger logger = Logger.getLogger(Office365ApiClient.class);
    public static final int PAGE_SIZE = 500;
    public static final String OFFICE_365_ID = "etc:attribute:office365:o365Id";
    private final String clientId;
    private final String clientSecret;
    private final String tenantId;
    private final String scope;
    private final Office365GraphApiService service;
    private final GrouperSession grouperSession;
    String token = null;
    private final IGraphServiceClient graphClient;
    protected O365UserLookup o365UserLookup;
    protected Gson gson;

    public Office365ApiClient(String clientId, String clientSecret, String tenantId, String scope, GrouperSession grouperSession) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tenantId = tenantId;
        this.scope = scope;

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        RetrofitWrapper retrofit = buildRetroFit(loggingInterceptor);

        this.graphClient = getiGraphServiceClient();

        this.service = retrofit.create(Office365GraphApiService.class);

        this.grouperSession = grouperSession;
        this.gson = new Gson();
        String userLookupClass = getUserLookupClass();
        buildO365UserLookupClass(userLookupClass);

    }

    protected RetrofitWrapper buildRetroFit(HttpLoggingInterceptor loggingInterceptor) {
        if (loggingInterceptor != null) {
            logger.debug("using client to build retrofit.");
            OkHttpClient client = buildOkHttpClient(loggingInterceptor);
            return new RetrofitWrapper((new Retrofit
                    .Builder()
                    .baseUrl("https://graph.microsoft.com/v1.0/")
                    .addConverterFactory(MoshiConverterFactory.create())
                    .client(client)
                    .build()));
        } else {
            logger.debug("not using client to build retrofit.");
            Retrofit data = new Retrofit.Builder()
                .baseUrl("https://login.microsoftonline.com/" + this.tenantId + "/")
                .addConverterFactory(MoshiConverterFactory.create())
                .build();
            return new RetrofitWrapper(data);
        }

    }

    protected void buildO365UserLookupClass(String userLookupClass) {
        Class cls = null;
        try {
            cls = Class.forName(userLookupClass);

            if (cls.equals(this.getClass())) {
                o365UserLookup = this;
            } else {
                o365UserLookup = (O365UserLookup) cls.newInstance();
                o365UserLookup.setApiClient(this);
            }
        } catch (InstantiationException | ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    protected String getUserLookupClass() {
        return GrouperO365Utils.configUserLookupClass();
    }

    protected OkHttpClient buildOkHttpClient(HttpLoggingInterceptor loggingInterceptor) {
        return new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request().newBuilder().header("Authorization", "Bearer " + token).build();
                        return chain.proceed(request);
                    }
                })
                .addInterceptor(loggingInterceptor)
                .build();
    }

    @Override
    public void setApiClient(Office365ApiClient client) {
        // do nothing.
    }

    public String getToken() throws IOException {
        logger.debug("Token client ID: " + this.clientId);
        logger.debug("Token tenant ID: " + this.tenantId);
        RetrofitWrapper retrofit = buildRetroFit(null);
        Office365AuthApiService service = retrofit.create(Office365AuthApiService.class);
        retrofit2.Response<OAuthTokenInfo> response = service.getOauth2Token(
                "client_credentials",
                this.clientId,
                this.clientSecret,
                this.scope,
                "https://graph.microsoft.com")
                .execute();
        if (response.isSuccessful()) {
            OAuthTokenInfo info = response.body();
            logTokenInfo(info);
            return info.accessToken;
        } else {
            ResponseBody errorBody = response.errorBody();
            throw new IOException("error requesting token (" + response.code() + "): " + errorBody.string());
        }
    }

    private void logTokenInfo(OAuthTokenInfo info) {
        logger.debug("Token scope: " + info.scope);
        logger.debug("Token expiresIn: " + info.expiresIn);
        logger.debug("Token expiresOn: " + info.expiresOn);
        logger.debug("Token resource: " + info.resource);
        logger.debug("Token tokenType: " + info.tokenType);
        logger.debug("Token notBefore: " + info.notBefore);
    }


    /*
   This method invokes a retrofit API call with retry.  If the first call returns 401 (unauthorized)
   the same is retried again after fetching a new token.
    */
    private <T> ResponseWrapper<T> invoke(retrofit2.Call<T> call) throws IOException {
        return invoke(call,false);
    }
    private <T> ResponseWrapper<T> invoke(retrofit2.Call<T> call,boolean doMembershipRemove) throws IOException {
        return invokeResponse(call,doMembershipRemove);
    }

    protected <T> ResponseWrapper<T> invokeResponse(retrofit2.Call<T> call,boolean doMembershipRemove) throws IOException {
        return new RetroFitInvoker<T>(this, call,doMembershipRemove).invoke();
    }

    public void addGroup(Group group) {
        if (group != null) {
            logger.debug("Creating group " + group);
            try {
                logger.debug("**** ");

                final ResponseWrapper response = invoke(this.service.createGroup(
                        new edu.internet2.middleware.grouper.changeLog.consumer.model.Group(
                                null,
                                group.getName(),
                                false,
                                group.getUuid(),
                                true,
                                new ArrayList<String>(),
                                group.getId()
                        )
                ));

                addIdToGroupAttribute(group, response);
            } catch (IOException e) {
                logger.error(e);
            }
        }
    }

    protected void addIdToGroupAttribute(Group group, ResponseWrapper response) {
        AttributeDefName attributeDefName = lookupOffice365IdAttributeDefName();
        group.getAttributeDelegate().assignAttribute(attributeDefName);
        group.getAttributeValueDelegate().assignValue(OFFICE_365_ID, ((edu.internet2.middleware.grouper.changeLog.consumer.model.Group) response.body()).id);
    }

    protected AttributeDefName lookupOffice365IdAttributeDefName() {
        return AttributeDefNameFinder.findByName("etc:attribute:office365:o365Id", false);
    }

    public void removeGroup(String groupName) {
        logger.debug("removing group " + groupName);
        try {
            Map options = new TreeMap<>();
            options.put("$filter", "displayName eq '" + groupName + "'");
            logger.debug("filter is " + "displayName eq '" + groupName + "'");
            final ResponseWrapper response = invoke(this.service.getGroups(options));
            logger.debug(response.body());
            edu.internet2.middleware.grouper.changeLog.consumer.model.GroupsOdata group = (edu.internet2.middleware.grouper.changeLog.consumer.model.GroupsOdata) response.body();
            logger.debug("group is " + group.groups.get(0).toString());
            invoke(this.service.deleteGroup(group.groups.get(0).id));
        } catch (IOException e) {
            logger.error(e);
        }
    }

    public GroupsOdata getAllGroups() {
        try {

            IGroupCollectionPage page = requestAllGroupsFromMS();
            GroupsOdata groupDataObject = createNewEmptyGroupsOData();
            do {
                if (haveAPageToProcess(page)) {
                    page = processPage(page, groupDataObject);
                }
            } while (shouldLoadNextPage(page));
            return groupDataObject;
        } catch (Exception e) {
            logger.error("problem", e);
        }
        return null;
    }

    protected IGroupCollectionPage processPage(IGroupCollectionPage page, GroupsOdata groupDataObject) {
        List<com.microsoft.graph.models.extensions.Group> groupDataPageList = page.getCurrentPage();
        addGroupsFromPage(groupDataObject, groupDataPageList);
        if (shouldLoadNextPage(page)) {
            page = getNextPageOfGroups(page);
        }
        return page;
    }

    protected GroupsOdata createNewEmptyGroupsOData() {
        return new GroupsOdata(null, new LinkedList<edu.internet2.middleware.grouper.changeLog.consumer.model.Group>(), null);
    }

    protected boolean haveAPageToProcess(IGroupCollectionPage page) {
        return page != null;
    }

    protected boolean shouldLoadNextPage(IGroupCollectionPage page) {
        return page != null && page.getNextPage() != null;
    }

    protected IGroupCollectionPage getNextPageOfGroups(IGroupCollectionPage page) {
        return page.getNextPage().buildRequest().get();
    }

    protected IGroupCollectionPage requestAllGroupsFromMS() {
        return graphClient.groups().buildRequest().top(PAGE_SIZE).get();
    }

    private void addGroupsFromPage(GroupsOdata groupDataObject, List<com.microsoft.graph.models.extensions.Group> groupDataPageList) {
        for (com.microsoft.graph.models.extensions.Group g : groupDataPageList) {
            logger.debug("adding " + g.displayName);
            groupDataObject.groups.add(new edu.internet2.middleware.grouper.changeLog.consumer.model.Group(g.id, g.displayName, g.mailEnabled, g.mailNickname, g.securityEnabled, null, g.description));

        }
    }

    public Members getMembersForGroup(Group group) {
        try {
            if (group != null) {
                String groupId = lookupOffice365GroupId(group);
                Members members = new Members("", new LinkedList<MemberUser>());
                if (groupId != null) {
                    IDirectoryObjectCollectionWithReferencesPage memberPage = getMembersOfGroupFromMS(groupId);
                    do {
                        memberPage = processPage(members, memberPage);
                    } while (shouldLoadNextPage(memberPage));
                }
                return members;
            }
        } catch (Exception e) {
            logger.error("problem", e);
        }
        return null;
    }

    protected IDirectoryObjectCollectionWithReferencesPage processPage(Members members, IDirectoryObjectCollectionWithReferencesPage memberPage) {
        if (haveAPageToProcess(memberPage)) {
            addMembersFromPage(members, memberPage);
            if (shouldLoadNextPage(memberPage)) {
                memberPage = getNextPageOfMembers(memberPage);
            }
        }
        return memberPage;
    }

    protected void addMembersFromPage(Members members, IDirectoryObjectCollectionWithReferencesPage memberPage) {
        Members members1 = (Members) gson.fromJson(memberPage.getRawObject().toString(), Members.class);
        members.users.addAll(members1.users);
    }

    protected boolean shouldLoadNextPage(IDirectoryObjectCollectionWithReferencesPage memberPage) {
        return memberPage != null && memberPage.getNextPage() != null;
    }

    protected boolean haveAPageToProcess(IDirectoryObjectCollectionWithReferencesPage memberPage) {
        return memberPage != null;
    }

    protected IDirectoryObjectCollectionWithReferencesPage getNextPageOfMembers(IDirectoryObjectCollectionWithReferencesPage memberPage) {
        return memberPage.getNextPage().buildRequest().get();
    }

    protected IDirectoryObjectCollectionWithReferencesPage getMembersOfGroupFromMS(String groupId) {
        return graphClient.groups(groupId).members().buildRequest().top(PAGE_SIZE).get();
    }

    private IGraphServiceClient getiGraphServiceClient() {
        GraphServiceClientManager provider = new GraphServiceClientManager();
        provider.setApiClient(this);
        return GraphServiceClient
                .builder()
                .authenticationProvider(provider)
                .buildClient();
    }


    public void addMembership(Subject subject, Group group) throws MissingUserException {
        if (group != null) {
            String groupId = lookupOffice365GroupId(group);
            if (groupId != null) {
                logger.debug("groupId: " + groupId);

                User user = lookupMSUser(subject);
                if (user != null) {
                    logger.debug("finalUser is " + user == null ? "null" : user.toString());
                    addMemberToMS(subject, groupId, user);
                } else {
                    throw new MissingUserException(subject);
                }
            }
        }
    }

    protected User lookupMSUser(Subject subject) {
        return o365UserLookup.getUser(subject, this.tenantId);
    }

    protected String lookupOffice365GroupId(Group group) {
        return group.getAttributeValueDelegate().retrieveValueString("etc:attribute:office365:o365Id");
    }

    protected void addMemberToMS(Subject subject, String groupId, User user) {
        try {
            invoke(this.service.addGroupMember(groupId, new OdataIdContainer("https://graph.microsoft.com/v1.0/users/" + user.userPrincipalName)));
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        } catch (MemberAddAlreadyExistsException me) {
            logger.debug("member already exists for subject:" + subject.getId() + " and group:" + groupId);
        }
    }

    @Override
    public User getUser(Subject subject, String domain) {
        User user = null;
        logger.debug("calling getUserFrom Office365ApiClient");
        try {

            user = invoke(this.service.getUserByUPN(subject.getAttributeValue("uid") + "@" + this.tenantId)).body();
            logger.debug("user = " + user.toString());
            return user;
        } catch (IOException e) {
            logger.debug("user wasn't found on default domain of " + domain);
        }
        return null;
    }

    public void removeMembership(Subject subject, Group group) throws MissingUserException {
        try {
            if (group != null) {
                User user = lookupMSUser(subject);
                if (user == null) {
                    throw new MissingUserException(subject);
                }
                String groupId = lookupOffice365GroupId(group);
                if (ifUserAndGroupExistInMS(user, groupId)) {
                    removeUserFromGroupInMS(user, groupId);
                }
            }
        } catch (IOException e) {
            logger.error(e);
        } catch (MemberDeleteAlreadyDeletedException me) {
            logger.debug("member already deleted for subject:" + subject.getId() + " and group:" + group.getId());
        }
    }

    protected boolean ifUserAndGroupExistInMS(User user, String groupId) {
        return user != null && groupId != null;
    }

    protected void removeUserFromGroupInMS(User user, String groupId) throws IOException {
        invoke(this.service.removeGroupMember(groupId, user.id),true);
    }


}
