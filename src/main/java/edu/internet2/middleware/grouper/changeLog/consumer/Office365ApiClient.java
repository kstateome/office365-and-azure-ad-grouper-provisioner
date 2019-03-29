package edu.internet2.middleware.grouper.changeLog.consumer;

import com.google.gson.Gson;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.http.IHttpRequest;
import com.microsoft.graph.logger.DefaultLogger;
import com.microsoft.graph.models.extensions.DirectoryObject;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.requests.extensions.GraphServiceClient;
import com.microsoft.graph.requests.extensions.IDirectoryObjectCollectionWithReferencesPage;
import com.microsoft.graph.requests.extensions.IGroupCollectionPage;
import com.microsoft.graph.serializer.AdditionalDataManager;
import com.microsoft.graph.serializer.DefaultSerializer;
import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.Stem;
import edu.internet2.middleware.grouper.StemFinder;
import edu.internet2.middleware.grouper.attr.AttributeDefName;
import edu.internet2.middleware.grouper.attr.finder.AttributeDefNameFinder;
import edu.internet2.middleware.grouper.changeLog.consumer.model.*;
import edu.internet2.middleware.subject.Subject;
import edu.ksu.ome.o365.grouper.GraphServiceClientManager;
import edu.ksu.ome.o365.grouper.MissingUserException;
import edu.ksu.ome.o365.grouper.O365GroupSync;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class interacts with the Microsoft Graph API.
 * Their Java client leaves much to be desired, however it handles pagination better than the 'Rest' version of the client.
 * So, pagination is being handled through the IGraphServiceClient,(getting members and list of all groups), while
 * individual stuff is using retrofit to call the rest API.
 */
public class Office365ApiClient {
    private static final Logger logger = Logger.getLogger(Office365ApiClient.class);
    public static final int PAGE_SIZE = 500;
    private final String clientId;
    private final String clientSecret;
    private final String tenantId;
    private final String scope;
    private final String subdomainStem;
    private final Office365GraphApiService service;
    private final GrouperSession grouperSession;
    private String token = null;
    private final IGraphServiceClient graphClient;
    protected Gson gson;

    public Office365ApiClient(String clientId, String clientSecret, String tenantId, String scope, String subdomainStem, GrouperSession grouperSession) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tenantId = tenantId;
        this.scope = scope;
        this.subdomainStem = subdomainStem;
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
            } else {
                throw new IOException("Unhandled invoke response (" + r.code() + ") " + r.errorBody().string());
            }
        }
        throw new IOException("Retry failed for: " + call.request().url());
    }

    public void addGroup(Group group) {
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
            String groupId = group.getAttributeValueDelegate().retrieveValueString("etc:attribute:office365:o365Id");
            Members members = new Members("", new LinkedList<MemberUser>());
            if (groupId != null) {
                IDirectoryObjectCollectionWithReferencesPage memberPage = graphClient.groups(groupId).members().buildRequest().top(PAGE_SIZE).get();
                do {
                    if (memberPage != null) {

                        Members members1 = (Members) gson.fromJson(memberPage.getRawObject().toString(),Members.class);
                        members.users.addAll(members1.users);
                    }
                    if (memberPage.getNextPage() != null) {
                        memberPage = memberPage.getNextPage().buildRequest().get();
                    }

                } while (memberPage.getNextPage() != null);
            }
            return members;
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


    public void addMembership(Subject subject, Group group) throws MissingUserException{
        String groupId = group.getAttributeValueDelegate().retrieveValueString("etc:attribute:office365:o365Id");
        if (groupId != null) {
            logger.debug("groupId: " + groupId);
            List<String> account = getAccount(subject);
            User user = getUserFromMultipleDomains(subject, account);
            if (user != null) {
                logger.debug("finalUser is " + user == null ? "null" : user.toString());
                try {
                    invoke(this.service.addGroupMember(groupId, new OdataIdContainer("https://graph.microsoft.com/v1.0/users/" + user.userPrincipalName)));
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            } else {
                throw new MissingUserException(subject, account);
            }
        }
    }

    public User getUser(Subject subject) {
        return getUserFromMultipleDomains(subject, getAccount(subject));
    }

    public void removeMembership(Subject subject, Group group) throws MissingUserException{
        try {
            List<String> account = getAccount(subject);
            User userFromMultipleDomains = getUserFromMultipleDomains(subject, account);
            if (userFromMultipleDomains == null) {

                throw new MissingUserException(subject, account);

            }
            String groupId = group.getAttributeValueDelegate().retrieveValueString("etc:attribute:office365:o365Id");
            if (userFromMultipleDomains != null && groupId != null) {
                invoke(this.service.removeGroupMember(groupId, userFromMultipleDomains.id));


            }
        } catch (IOException e) {
            logger.error(e);
        }
    }



    private User getUserFromMultipleDomains(Subject subject, List<String> possibleDomains) {
        User user = null;
        try {

            user = invoke(this.service.getUserByUPN(subject.getAttributeValue("uid") + "@" + this.tenantId)).body();
            logger.debug("user = " + user.toString());
        } catch (IOException e) {
            logger.debug("user wasn't found on default domain");
        }
        User foundUser = null;
        if (!possibleDomains.isEmpty() && user == null) {
            // find ids..
            for (String domain : possibleDomains) {
                try {
                    logger.debug("trying " + subject.getAttributeValue("uid") + "@" + domain.trim());
                    user = invoke(this.service.getUserByUPN(subject.getAttributeValue("uid") + "@" + domain.trim())).body();
                    if (user != null) {
                        logger.debug("user was found" + user.userPrincipalName);
                        foundUser = user;
                    }
                } catch (IOException e) {
                    logger.debug("user wasn't found on " + domain + " domain");
                }

            }
        }
        if (foundUser != null) {
            user = foundUser;
        }
        return user;
    }

    /**
     * searches a stem to get a list of possible domain names other than the default one.
     *
     * @param subject
     * @return
     */
    private List<String> getAccount(Subject subject) {
        List<String> possibleDomains = new LinkedList<>();
        Stem stem = StemFinder.findByName(grouperSession, subdomainStem, false);
        Set<Stem> childStems = stem.getChildStems();
        for (Stem child : childStems) {
            for (Object childGroupObject : child.getChildGroups()) {
                Group childGroup = (Group) childGroupObject;
                if (childGroup.hasMember(subject)) {
                    logger.debug("domain = " + childGroup.getName());
                    String domain = childGroup.getName();
                    String[] domainData = domain.split("[:]");
                    domain = domainData[domainData.length - 1];
                    possibleDomains.add(domain);
                }
            }
        }
        return possibleDomains;
    }

}
