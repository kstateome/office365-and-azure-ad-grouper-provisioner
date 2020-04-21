package edu.internet2.middleware.grouper.changeLog.consumer;


import edu.internet2.middleware.grouper.*;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.app.loader.OtherJobBase;
import edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBaseImpl;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.pit.PITGroup;
import edu.internet2.middleware.subject.Subject;
import edu.ksu.ome.o365.grouper.MissingUserException;
import edu.ksu.ome.o365.grouper.O365SingleFullGroupSync;
import org.apache.log4j.Logger;

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
    public static final String CONFIG_PREFIX = "changeLog.consumer.";

    private String token = null;
    private  String clientId;
    private  String clientSecret;
    private  String tenantId;
    private  String scope;
    private String nameOfConsumer;
    private  Office365ApiClient apiClient;
    private static ScheduledExecutorService scheduledExecutorService;
    public static Map<String, Long> lastScheduledMap;
    private static final long scheduleBuffer = 1000 * 60 * 15;// 15 minutes

    public enum AzureGroupType {Security,Unified,MailEnabled,MailEnabledSecurity}

    private GrouperSession grouperSession = null;

    public Office365ChangeLogConsumer() {

    }
    public Office365ChangeLogConsumer(String nameOfConsumer){
        this.nameOfConsumer = nameOfConsumer;
        initConsumer(nameOfConsumer);
    }

    protected void initConsumer(String name) {

        this.clientId = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired(CONFIG_PREFIX + name + ".clientId");
        this.clientSecret = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired(CONFIG_PREFIX + name + ".clientSecret");
        this.tenantId = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired(CONFIG_PREFIX + name + ".tenantId");
        this.scope = GrouperLoaderConfig.retrieveConfig().propertyValueString(CONFIG_PREFIX + name + ".scope", "https://graph.microsoft.com/.default");

        this.grouperSession = GrouperSession.startRootSession();


        AzureGroupType groupType = getAzureGroupType(name);
        edu.internet2.middleware.grouper.changeLog.consumer.model.Group.Visibility visibility = getAzureVisibility(name, groupType);

        this.apiClient = new Office365ApiClient(clientId, clientSecret, tenantId, scope, groupType,visibility, grouperSession);
        if (scheduledExecutorService == null) {
            scheduledExecutorService = Executors.newScheduledThreadPool(1);
        }
        if (lastScheduledMap == null) {
            lastScheduledMap = new ConcurrentHashMap<>();
        }
    }

    protected edu.internet2.middleware.grouper.changeLog.consumer.model.Group.Visibility getAzureVisibility(String name, AzureGroupType groupType) {
        edu.internet2.middleware.grouper.changeLog.consumer.model.Group.Visibility visibility = null;
        String visibilityString = GrouperLoaderConfig.retrieveConfig().propertyValueString(CONFIG_PREFIX + name + ".visibility", edu.internet2.middleware.grouper.changeLog.consumer.model.Group.Visibility.Public .name());
        if (visibilityString != null) {
            if (groupType == AzureGroupType.Unified) {
                try {
                    visibility = edu.internet2.middleware.grouper.changeLog.consumer.model.Group.Visibility.valueOf(visibilityString);
                } catch (IllegalArgumentException e) {
                    visibility = edu.internet2.middleware.grouper.changeLog.consumer.model.Group.Visibility.Public;
                    logger.error("consumer " + this.getConsumerName() + ": Invalid option for property " + CONFIG_PREFIX + name + ".visibility: " + visibilityString + " - reverting to type " + visibility.name());
                }
            } else {
                logger.error("consumer " + this.getConsumerName() + ": Property " + CONFIG_PREFIX + name + ".visibility is only valid for Unified group type -- ignoring");
            }
        }
        return visibility;
    }

    protected AzureGroupType getAzureGroupType(String name) {
        AzureGroupType groupType;
        String groupTypeString =  GrouperLoaderConfig.retrieveConfig().propertyValueString(CONFIG_PREFIX + name + ".groupType", AzureGroupType.Security.name());
        try {
            groupType = AzureGroupType.valueOf(groupTypeString);
        } catch (IllegalArgumentException e) {
            groupType = AzureGroupType.Security;
            logger.error("consumer " + this.getConsumerName() + ": Invalid option for property " + CONFIG_PREFIX + name + ".groupType: " + groupTypeString + " - reverting to type " + groupType.name());
        }
        return groupType;
    }

    @Override
    public long processChangeLogEntries(List<ChangeLogEntry> changeLogEntryList,
                                        ChangeLogProcessorMetadata changeLogProcessorMetadata) {
        String name = changeLogProcessorMetadata.getConsumerName() != null ? changeLogProcessorMetadata.getConsumerName() : "o365";
        nameOfConsumer = name;
        initConsumer(name);
        return super.processChangeLogEntries(changeLogEntryList,changeLogProcessorMetadata);
    }
    public Office365ChangeLogConsumer(OtherJobBase.OtherJobInput input) {
        // TODO: this.getConsumerName() isn't working for some reason. track down
        logger.info("jobname = " + input.getJobName());
        String name = input.getJobName().substring(input.getJobName().lastIndexOf("_")+1);
        nameOfConsumer = name;
        logger.info("name = " +name);
        initConsumer(name);
    }

    public Office365ApiClient getApiClient() {
        return apiClient;
    }

    @Override
    protected void addGroup(Group group, ChangeLogEntry changeLogEntry) {
        if(group != null) {
            apiClient.addGroup(group);
        }

    }

    @Override
    protected void addGroupAndMemberships(Group group, ChangeLogEntry changeLogEntry) {
        if(group != null) {
            addGroup(group, changeLogEntry);
            scheduleFullSyncOfGroup(group);
        }
    }

    public String getTenantId() {
        return tenantId;
    }

    // TODO: find out how to induce and implement (if necessary)
    @Override
    protected void removeGroup(Group group, ChangeLogEntry changeLogEntry) {
        if(group != null) {
            logger.debug("removing group " + group);
            String id = group.getAttributeValueDelegate().retrieveValuesString("etc:attribute:office365:o365Id").get(0);
            logger.debug("removing id: " + id);
        }

    }

    @Override
    protected void removeDeletedGroup(PITGroup pitGroup, ChangeLogEntry changeLogEntry) {
        if(pitGroup != null) {
            logger.debug("removing group " + pitGroup + ": " + pitGroup.getId());
            apiClient.removeGroup(pitGroup.getName());
        }

    }

    @Override
    protected void addMembership(Subject subject, Group group, ChangeLogEntry changeLogEntry) {
        if(subject != null && group != null) {
            logger.debug("adding " + subject + " to " + group);
            logger.debug("attributes: " + subject.getAttributes());
            try {
                apiClient.addMembership(subject, group);
            } catch (MissingUserException e) {
                scheduleFullSyncOfGroup(group);
            }
        }

    }

    private void scheduleFullSyncOfGroup(Group group) {
        if (!lastScheduledMap.containsKey(group.getName()) || lastScheduledMap.get(group.getName()) < System.currentTimeMillis()) {
            Map<String, Object> debugMap = new LinkedHashMap<String, Object>();
            scheduledExecutorService.schedule(new O365SingleFullGroupSync(debugMap, group, 0, 0, 0, 0, GrouperO365Utils.configSourcesForSubjects(), GrouperO365Utils.configSubjectAttributeForO365Username(),nameOfConsumer), 30, TimeUnit.MINUTES);
            lastScheduledMap.put(group.getName(), System.currentTimeMillis() + scheduleBuffer);// prevent lots of full syncs from happening.
        }
    }


    @Override
    protected void removeMembership(Subject subject, Group group, ChangeLogEntry changeLogEntry) {
        if(subject != null && group != null) {
            logger.debug("removing " + subject + " from " + group);
            try {
                apiClient.removeMembership(subject, group);
            } catch (MissingUserException e) {
                scheduleFullSyncOfGroup(group);
            }
        }
    }
}
