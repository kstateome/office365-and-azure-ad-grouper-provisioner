package edu.internet2.middleware.grouper.changeLog.consumer;


import edu.internet2.middleware.grouper.*;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.app.loader.OtherJobBase;
import edu.internet2.middleware.grouper.attr.AttributeDefName;
import edu.internet2.middleware.grouper.attr.finder.AttributeDefNameFinder;
import edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBaseImpl;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.consumer.model.OAuthTokenInfo;
import edu.internet2.middleware.grouper.changeLog.consumer.model.OdataIdContainer;
import edu.internet2.middleware.grouper.changeLog.consumer.model.User;
import edu.internet2.middleware.grouper.pit.PITGroup;
import edu.internet2.middleware.subject.Subject;
import edu.ksu.ome.o365.grouper.MissingUserException;
import edu.ksu.ome.o365.grouper.O365GroupSync;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.log4j.Logger;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by jj on 5/30/16.
 */
public class Office365ChangeLogConsumer extends ChangeLogConsumerBaseImpl {
    private static final Logger logger = Logger.getLogger(Office365ChangeLogConsumer.class);
    private static final String CONFIG_PREFIX = "changeLog.consumer.";

    private String token = null;
    private final String clientId;
    private final String clientSecret;
    private final String tenantId;
    private final String scope;
    private final String subdomainStem;
    private final Office365ApiClient apiClient;
    private static ScheduledExecutorService scheduledExecutorService;
    private static Map<String, Long> lastScheduledMap;
    private static final long scheduleBuffer = 1000 * 60 * 3;// 3 minutes



    private final GrouperSession grouperSession;

    public Office365ChangeLogConsumer() {
        // TODO: this.getConsumerName() isn't working for some reason. track down
        String name = this.getConsumerName() != null ? this.getConsumerName() : "o365";
        this.clientId = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired(CONFIG_PREFIX + name + ".clientId");
        this.clientSecret = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired(CONFIG_PREFIX + name + ".clientSecret");
        this.tenantId = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired(CONFIG_PREFIX + name + ".tenantId");
        this.scope = GrouperLoaderConfig.retrieveConfig().propertyValueString(CONFIG_PREFIX + name + ".scope", "https://graph.microsoft.com/.default");
        this.subdomainStem = GrouperLoaderConfig.retrieveConfig().propertyValueString(CONFIG_PREFIX + name + ".subdomainStem", "ksu:NotInLdapApplications:office365:subdomains");

        this.grouperSession = GrouperSession.startRootSession();
        this.apiClient = new Office365ApiClient(clientId, clientSecret, tenantId, scope, subdomainStem,grouperSession);
        if(scheduledExecutorService == null){
            scheduledExecutorService = Executors.newScheduledThreadPool(1);
        }
        if(lastScheduledMap == null){
            lastScheduledMap = new ConcurrentHashMap<>();
        }

    }

    public Office365ChangeLogConsumer(OtherJobBase.OtherJobInput input) {
        // TODO: this.getConsumerName() isn't working for some reason. track down
        String name = this.getConsumerName() != null ? this.getConsumerName() : "o365";
        this.clientId = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired(CONFIG_PREFIX + name + ".clientId");
        this.clientSecret = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired(CONFIG_PREFIX + name + ".clientSecret");
        this.tenantId = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired(CONFIG_PREFIX + name + ".tenantId");
        this.scope = GrouperLoaderConfig.retrieveConfig().propertyValueString(CONFIG_PREFIX + name + ".scope", "https://graph.microsoft.com/.default");
        this.subdomainStem = GrouperLoaderConfig.retrieveConfig().propertyValueString(CONFIG_PREFIX + name + ".subdomainStem", "ksu:NotInLdapApplications:office365:subdomains");

        this.apiClient = new Office365ApiClient(clientId, clientSecret, tenantId, scope, subdomainStem,input.getGrouperSession());
        this.grouperSession = input.getGrouperSession();
        if(scheduledExecutorService == null){
            scheduledExecutorService = Executors.newScheduledThreadPool(1);
        }
        if(lastScheduledMap == null){
            lastScheduledMap = new ConcurrentHashMap<>();
        }
    }

    public Office365ApiClient getApiClient() {
        return apiClient;
    }

    @Override
    protected void addGroup(Group group, ChangeLogEntry changeLogEntry) {
        apiClient.addGroup(group);

    }

    // TODO: find out how to induce and implement (if necessary)
    @Override
    protected void removeGroup(Group group, ChangeLogEntry changeLogEntry) {
        logger.debug("removing group " + group);
        String id = group.getAttributeValueDelegate().retrieveValuesString("etc:attribute:office365:o365Id").get(0);
        logger.debug("removing id: " + id);

    }

    @Override
    protected void removeDeletedGroup(PITGroup pitGroup, ChangeLogEntry changeLogEntry) {
        logger.debug("removing group " + pitGroup + ": " + pitGroup.getId());
        apiClient.removeGroup(pitGroup.getName());

    }

    @Override
    protected void addMembership(Subject subject, Group group, ChangeLogEntry changeLogEntry) {
        logger.debug("adding " + subject + " to " + group);
        logger.debug("attributes: " + subject.getAttributes());
        try {
            apiClient.addMembership(subject,group);
        } catch (MissingUserException e) {
            scheduleFullSyncOfGroup(group);
        }

    }

    private void scheduleFullSyncOfGroup(Group group) {
        if (!lastScheduledMap.containsKey(group.getName()) || lastScheduledMap.get(group.getName()) < System.currentTimeMillis()) {
            Map<String, Object> debugMap = new LinkedHashMap<String, Object>();
            scheduledExecutorService.schedule(new O365GroupSync(debugMap, group, 0, 0, 0, 0, GrouperO365Utils.configSourcesForSubjects(), GrouperO365Utils.configSubjectAttributeForO365Username()), 5, TimeUnit.MINUTES);
            lastScheduledMap.put(group.getName(), System.currentTimeMillis() + scheduleBuffer);// prevent lots of full syncs from happening.
        }
    }



    @Override
    protected void removeMembership(Subject subject, Group group, ChangeLogEntry changeLogEntry) {
        logger.debug("removing " + subject + " from " + group);
        try {
            apiClient.removeMembership(subject,group);
        } catch (MissingUserException e) {
            scheduleFullSyncOfGroup(group);
        }
    }
}