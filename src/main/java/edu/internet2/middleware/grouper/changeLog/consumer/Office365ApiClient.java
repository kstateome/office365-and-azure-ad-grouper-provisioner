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
    private final String clientId;
    private final String clientSecret;
    private final String tenantId;
    private final String scope;
    private final Office365GraphApiService service;
    private final GrouperSession grouperSession;
    private String token = null;
    private final IGraphServiceClient graphClient;
    private O365UserLookup o365UserLookup;
    protected Gson gson;

    public Office365ApiClient(String clientId, String clientSecret, String tenantId, String scope, GrouperSession grouperSession) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tenantId = tenantId;
        this.scope = scope;

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request().newBuilder().header("Authorization", "Bearer " + token).build();
                        return chain.proceed(request);
                    }
                })
                .addInterceptor(loggingInterceptor)
                .build();
        Retrofit retrofit = new Retrofit
                .Builder()
                .baseUrl("https://graph.microsoft.com/v1.0/")
                .addConverterFactory(MoshiConverterFactory.create())
                .client(client)
                .build();

        this.graphClient = getiGraphServiceClient();

        this.service = retrofit.create(Office365GraphApiService.class);

        this.grouperSession = grouperSession;
        this.gson = new Gson();
        String userLookupClass = GrouperO365Utils.configUserLookupClass();
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

    @Override
    public void setApiClient(Office365ApiClient client) {
        // do nothing.
    }

    public String getToken() throws IOException {
        logger.debug("Token client ID: " + this.clientId);
        logger.debug("Token tenant ID: " + this.tenantId);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://login.microsoftonline.com/" + this.tenantId + "/")
                .addConverterFactory(MoshiConverterFactory.create())
                .build();
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
            logger.debug("Token scope: " + info.scope);
            logger.debug("Token expiresIn: " + info.expiresIn);
            logger.debug("Token expiresOn: " + info.expiresOn);
            logger.debug("Token resource: " + info.resource);
            logger.debug("Token tokenType: " + info.tokenType);
            logger.debug("Token notBefore: " + info.notBefore);
            return info.accessToken;
        } else {
            ResponseBody errorBody = response.errorBody();
            throw new IOException("error requesting token (" + response.code() + "): " + errorBody.string());
        }
    }


    /*
   This method invokes a retrofit API call with retry.  If the first call returns 401 (unauthorized)
   the same is retried again after fetching a new token.
    */
    private <T> retrofit2.Response<T> invoke(retrofit2.Call<T> call) throws IOException {
        for (int retryMax = 2; retryMax > 0; retryMax--) {
            if (token == null) {
                token = getToken();
            }
            try {
                retrofit2.Response<T> r = call.execute();

                if (r.isSuccessful()) {
                    return r;
                } else if (r.code() == 401) {
                    logger.debug("auth fail, retry: " + call.request().url());
                    // Call objects cannot be reused, so docs say to use clone() to create a new one with the
                    // same specs for retry purposes
                    call = call.clone();
                    // null out existing token so we'll fetch a new one on next loop pass
                    token = null;
                } else if (r.code() == 400) {
                    if (r.message().contains("One or more added object references already exist")) {
                        // this was an add, but the user already existed..
                        throw new MemberAddAlreadyExistsException("member is already a member of the group in O365");
                    }
                } else if (r.code() == 404) {
                    if (r.message().contains("Request_ResourceNotFound")) {
                        // this was a delete, but the user was already deleted..
                        throw new MemberDeleteAlreadyDeletedException("member is already a deleted from the group in O365");
                    }
                } else {
                    throw new IOException("Unhandled invoke response (" + r.code() + ") " + r.errorBody().string());
                }
            } catch (IllegalStateException i) {
                if (!i.getMessage().contains("Already executed")) {
                    throw i;
                }
            }
        }
        throw new IOException("Retry failed for: " + call.request().url());
    }

    public void addGroup(Group group) {
        if (group != null) {
            logger.debug("Creating group " + group);
            try {
                logger.debug("**** ");

                retrofit2.Response response = invoke(this.service.createGroup(
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

                AttributeDefName attributeDefName = AttributeDefNameFinder.findByName("etc:attribute:office365:o365Id", false);
                group.getAttributeDelegate().assignAttribute(attributeDefName);
                group.getAttributeValueDelegate().assignValue("etc:attribute:office365:o365Id", ((edu.internet2.middleware.grouper.changeLog.consumer.model.Group) response.body()).id);
            } catch (IOException e) {
                logger.error(e);
            }
        }
    }

    public void removeGroup(String groupName) {
        logger.debug("removing group " + groupName);
        try {
            Map options = new TreeMap<>();
            options.put("$filter", "displayName eq '" + groupName + "'");
            logger.debug("filter is " + "displayName eq '" + groupName + "'");
            retrofit2.Response response = invoke(this.service.getGroups(options));
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

            IGroupCollectionPage page = graphClient.groups().buildRequest().top(PAGE_SIZE).get();
            GroupsOdata groupDataObject = new GroupsOdata(null, new LinkedList<edu.internet2.middleware.grouper.changeLog.consumer.model.Group>(), null);
            List<com.microsoft.graph.models.extensions.Group> groupDataPageList = page.getCurrentPage();
            for (com.microsoft.graph.models.extensions.Group g : groupDataPageList) {
                logger.debug("adding " + g.displayName);
                groupDataObject.groups.add(new edu.internet2.middleware.grouper.changeLog.consumer.model.Group(g.id, g.displayName, g.mailEnabled, g.mailNickname, g.securityEnabled, null, g.description));

            }
            do {
                if (page.getNextPage() != null) {
                    page = page.getNextPage().buildRequest().get();
                    groupDataPageList = page.getCurrentPage();
                    if (groupDataPageList != null && !groupDataPageList.isEmpty()) {
                        for (com.microsoft.graph.models.extensions.Group g : groupDataPageList) {
                            logger.debug("adding " + g.displayName);
                            groupDataObject.groups.add(new edu.internet2.middleware.grouper.changeLog.consumer.model.Group(g.id, g.displayName, g.mailEnabled, g.mailNickname, g.securityEnabled, null, g.description));

                        }
                    }
                } else {
                    groupDataPageList = null;
                }

            } while (groupDataPageList != null && !groupDataPageList.isEmpty());


            return groupDataObject;
        } catch (Exception e) {
            logger.error("problem", e);
        }
        return null;
    }

    public Members getMembersForGroup(Group group) {
        try {
            if (group != null) {
                String groupId = group.getAttributeValueDelegate().retrieveValueString("etc:attribute:office365:o365Id");
                Members members = new Members("", new LinkedList<MemberUser>());
                if (groupId != null) {
                    IDirectoryObjectCollectionWithReferencesPage memberPage = graphClient.groups(groupId).members().buildRequest().top(PAGE_SIZE).get();
                    do {
                        if (memberPage != null) {

                            Members members1 = (Members) gson.fromJson(memberPage.getRawObject().toString(), Members.class);
                            members.users.addAll(members1.users);
                        }
                        if (memberPage.getNextPage() != null) {
                            memberPage = memberPage.getNextPage().buildRequest().get();
                        }

                    } while (memberPage.getNextPage() != null);
                }
                return members;
            }
        } catch (Exception e) {
            logger.error("problem", e);
        }
        return null;
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
            String groupId = group.getAttributeValueDelegate().retrieveValueString("etc:attribute:office365:o365Id");
            if (groupId != null) {
                logger.debug("groupId: " + groupId);

                User user = o365UserLookup.getUser(subject, this.tenantId);
                if (user != null) {
                    logger.debug("finalUser is " + user == null ? "null" : user.toString());
                    try {
                        invoke(this.service.addGroupMember(groupId, new OdataIdContainer("https://graph.microsoft.com/v1.0/users/" + user.userPrincipalName)));
                    } catch (IOException e) {
                        logger.error(e.getMessage(), e);
                    } catch (MemberAddAlreadyExistsException me) {
                        logger.debug("member already exists for subject:" + subject.getId() + " and group:" + groupId);
                    }
                } else {
                    throw new MissingUserException(subject);
                }
            }
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
                User user = o365UserLookup.getUser(subject, this.tenantId);
                if (user == null) {

                    throw new MissingUserException(subject);

                }
                String groupId = group.getAttributeValueDelegate().retrieveValueString("etc:attribute:office365:o365Id");
                if (user != null && groupId != null) {
                    invoke(this.service.removeGroupMember(groupId, user.id));


                }
            }
        } catch (IOException e) {
            logger.error(e);
        } catch (MemberDeleteAlreadyDeletedException me) {
            logger.debug("member already deleted for subject:" + subject.getId() + " and group:" + group.getId());
        }
    }


}
