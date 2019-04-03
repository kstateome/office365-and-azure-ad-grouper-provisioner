package edu.ksu.ome.o365.grouper;
import edu.internet2.middleware.grouper.*;
import edu.internet2.middleware.grouper.app.loader.OtherJobBase;
import edu.internet2.middleware.grouper.Stem.Scope;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderScheduleType;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderStatus;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderType;
import edu.internet2.middleware.grouper.app.loader.db.Hib3GrouperLoaderLog;
import edu.internet2.middleware.grouper.changeLog.consumer.GrouperO365Utils;
import edu.internet2.middleware.grouper.changeLog.consumer.Office365ApiClient;
import edu.internet2.middleware.grouper.changeLog.consumer.Office365ChangeLogConsumer;
import edu.internet2.middleware.grouper.changeLog.consumer.model.GroupsOdata;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.quartz.DisallowConcurrentExecution;

import java.sql.Timestamp;
import java.util.*;

@DisallowConcurrentExecution
public class Office365FullRefresh extends OtherJobBase {
    public static final String GROUPER_O365_FULL_REFRESH = "CHANGE_LOG_grouperO365FullRefresh";
    private static final Log LOG = GrouperUtil.getLog(Office365FullRefresh.class);
    private Office365ApiClient apiClient;


    public static void main(String[] args) {
        Office365FullRefresh refresh = new Office365FullRefresh();
        refresh.fullRefreshLogic();
    }

    /**
     *
     */
    public  void fullRefreshLogic() {
        OtherJobInput otherJobInput = new OtherJobInput();
        GrouperSession grouperSession = GrouperSession.startRootSession();
        otherJobInput.setGrouperSession(grouperSession);
        Hib3GrouperLoaderLog hib3GrouploaderLog = new Hib3GrouperLoaderLog();
        otherJobInput.setHib3GrouperLoaderLog(hib3GrouploaderLog);
        try {
            fullRefreshLogic(otherJobInput);
        } finally {
            GrouperSession.stopQuietly(grouperSession);
        }
    }

    public static void doFullRefresh(){
        Office365FullRefresh fullRefresh = new Office365FullRefresh();
        fullRefresh.fullRefreshLogic();;
    }

    public  void fullRefreshLogic(OtherJobInput otherJobInput) {
        GrouperSession grouperSession = otherJobInput.getGrouperSession();
        Office365ChangeLogConsumer temp = new Office365ChangeLogConsumer(otherJobInput);
        apiClient = temp.getApiClient();
        Map<String, Object> debugMap = new LinkedHashMap<String, Object>();

        long startTimeNanos = System.nanoTime();

        debugMap.put("method", "fullRefreshLogic");

        //lets enter a log entry so it shows up as error in the db
        Hib3GrouperLoaderLog hib3GrouploaderLog = otherJobInput.getHib3GrouperLoaderLog();
        hib3GrouploaderLog.setHost(GrouperUtil.hostname());
        hib3GrouploaderLog.setJobName(GROUPER_O365_FULL_REFRESH);
        hib3GrouploaderLog.setJobScheduleType(GrouperLoaderScheduleType.CRON.name());
        hib3GrouploaderLog.setJobType(GrouperLoaderType.MAINTENANCE.name());

        hib3GrouploaderLog.setStartedTime(new Timestamp(System.currentTimeMillis()));

        long startedMillis = System.currentTimeMillis();

        try {

            //# put groups in here which go to o365, the name in o365 will be the extension here
            //grouperO365.folder.name.withO365Groups = o365
            String grouperO365FolderName = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired("grouperO365.folder.name.witho365Groups");
            Stem grouperO365Folder = StemFinder.findByName(grouperSession, grouperO365FolderName, true);

            Set<Group> grouperGroups = grouperO365Folder.getChildGroups(Scope.ONE);



            //make a map from group extension
            Map<String, Group> groupsInGrouper = new HashMap<String, Group>();

            for (Group group : grouperGroups) {
                groupsInGrouper.put(group.getName(), group);
            }

            //get groups from o365
            Map<String, edu.internet2.middleware.grouper.changeLog.consumer.model.Group> groupsInOffice365 = getAllSecurityGroups(grouperO365FolderName);
            LOG.error("map size is " + groupsInOffice365.size()) ;
            debugMap.put("o365TotalGroupCount", groupsInOffice365.size());

            debugMap.put("millisGetData", System.currentTimeMillis() - startedMillis);
            hib3GrouploaderLog.setMillisGetData((int)(System.currentTimeMillis() - startedMillis));
            long startedUpdateData = System.currentTimeMillis();

            boolean needsGroupRefresh = false;

            int insertCount = 0;
            int deleteCount = 0;
            int unresolvableCount = 0;
            int totalCount = 0;

            //# is grouper the true system of record, delete O365 groups which dont exist in grouper
            if (GrouperLoaderConfig.retrieveConfig().propertyValueBoolean("grouperO365.deleteSecurityGroupsInO365WhichArentInGrouper", false)) {
                deleteCount = deleteGroupsFromOffice365NotInGrouper(debugMap, groupsInGrouper, groupsInOffice365, deleteCount);

            }

            //loop through groups in grouper
            for (String groupNameInGrouper : groupsInGrouper.keySet()) {
                insertCount = addGroupsToOffice365ThatAreInGrouper(debugMap, groupsInGrouper, groupsInOffice365, insertCount, groupNameInGrouper);
            }



            //# put the comma separated list of sources to send to O365
            //grouperO365.sourcesForSubjects = pennperson
            Set<String> sourcesForSubjects = GrouperO365Utils.configSourcesForSubjects();

            //# either have id for subject id or an attribute for the O365 username (e.g. netId)
            //grouperO365.subjectAttributeForO365Username = pennname
           String subjectAttributeForO365Username = GrouperO365Utils.configSubjectAttributeForO365Username();

            //loop through groups in grouper
            for (String groupExtensionInGrouper : groupsInGrouper.keySet()) {
                O365SingleFullGroupSync o365SingleFullGroupSync = new O365SingleFullGroupSync(debugMap, groupsInGrouper.get(groupExtensionInGrouper), insertCount, deleteCount, unresolvableCount, totalCount, sourcesForSubjects, subjectAttributeForO365Username).invoke();
                insertCount = o365SingleFullGroupSync.getInsertCount();
                deleteCount = o365SingleFullGroupSync.getDeleteCount();
                unresolvableCount = o365SingleFullGroupSync.getUnresolvableCount();
                totalCount = o365SingleFullGroupSync.getTotalCount();

            }
            debugMap.put("millisLoadData", System.currentTimeMillis() - startedUpdateData);
            hib3GrouploaderLog.setMillisLoadData((int)(System.currentTimeMillis() - startedUpdateData));
            debugMap.put("millis", System.currentTimeMillis() - startedMillis);
            hib3GrouploaderLog.setEndedTime(new Timestamp(System.currentTimeMillis()));
            hib3GrouploaderLog.setMillis((int)(System.currentTimeMillis() - startedMillis));

            //lets enter a log entry so it shows up as error in the db
            hib3GrouploaderLog.setJobMessage(GrouperUtil.mapToString(debugMap));
            hib3GrouploaderLog.setStatus(GrouperLoaderStatus.SUCCESS.name());
            hib3GrouploaderLog.setUnresolvableSubjectCount(unresolvableCount);
            hib3GrouploaderLog.setInsertCount(insertCount);
            hib3GrouploaderLog.setDeleteCount(deleteCount);
            hib3GrouploaderLog.setTotalCount(totalCount);
            hib3GrouploaderLog.store();

        } catch (Exception e) {
            debugMap.put("exception", ExceptionUtils.getFullStackTrace(e));
            String errorMessage = "Problem running job: '" + GrouperLoaderType.GROUPER_CHANGE_LOG_TEMP_TO_CHANGE_LOG + "'";
            LOG.error(errorMessage, e);
            errorMessage += "\n" + ExceptionUtils.getFullStackTrace(e);
            try {
                //lets enter a log entry so it shows up as error in the db
                hib3GrouploaderLog.setMillis((int)(System.currentTimeMillis() - startedMillis));
                hib3GrouploaderLog.setEndedTime(new Timestamp(System.currentTimeMillis()));
                hib3GrouploaderLog.setJobMessage(errorMessage);
                hib3GrouploaderLog.setStatus(GrouperLoaderStatus.CONFIG_ERROR.name());
                hib3GrouploaderLog.store();

            } catch (Exception e2) {
                LOG.error("Problem logging to loader db log", e2);
            }

        } finally {
            if (debugMap != null ) {
                debugMap.put("elapsedMillis", (System.nanoTime() - startTimeNanos) / 1000000);
            }
            LOG.debug(GrouperUtil.mapToString(debugMap));

        }
    }

    private int addGroupsToOffice365ThatAreInGrouper(Map<String, Object> debugMap, Map<String, Group> groupsInGrouper, Map<String, edu.internet2.middleware.grouper.changeLog.consumer.model.Group> groupsInOffice365, int insertCount, String groupNameInGrouper) {
        boolean needsGroupRefresh;
        edu.internet2.middleware.grouper.changeLog.consumer.model.Group groupToAddToO365 = groupsInOffice365.get(groupNameInGrouper);

        if (groupToAddToO365 == null) {
            //create o365 group
            apiClient.addGroup(groupsInGrouper.get(groupNameInGrouper));
            needsGroupRefresh = true;
            debugMap.put("createO365Group_" + groupNameInGrouper, true);
            insertCount++;
        }
        return insertCount;
    }

    private int deleteGroupsFromOffice365NotInGrouper(Map<String, Object> debugMap, Map<String, Group> groupsInGrouper, Map<String, edu.internet2.middleware.grouper.changeLog.consumer.model.Group> groupsInOffice365, int deleteCount) {
        boolean needsGroupRefresh;//which groups are in O365 and not in grouper?
        Set<String> groupNamesNotInO365 = new TreeSet<String>(groupsInOffice365.keySet());
        groupNamesNotInO365.removeAll(groupsInGrouper.keySet());

        for (String groupNamesToRemove : groupNamesNotInO365) {
            apiClient.removeGroup(groupNamesToRemove);
            deleteCount++;
            debugMap.put("deleteO365Group_" + groupNamesToRemove, true);
            needsGroupRefresh = true;
        }
        return deleteCount;
    }

    private Map<String, edu.internet2.middleware.grouper.changeLog.consumer.model.Group> getAllSecurityGroups(String grouperO365FolderName) {
        GroupsOdata groupsOdata = apiClient.getAllGroups();
        Map<String, edu.internet2.middleware.grouper.changeLog.consumer.model.Group> mapToGroupName = new HashMap<>();
        for(edu.internet2.middleware.grouper.changeLog.consumer.model.Group o365Group : groupsOdata.groups){
            LOG.error("group found is " + o365Group.displayName);
            if(o365Group.securityEnabled && o365Group.displayName.startsWith(grouperO365FolderName)) {
                mapToGroupName.put(o365Group.displayName, o365Group);
            }
        }
        return mapToGroupName;
    }

    @Override
    public OtherJobOutput run(OtherJobInput otherJobInput) {
        OtherJobOutput otherJobOutput = new OtherJobOutput();

        fullRefreshLogic(otherJobInput);

        return otherJobOutput;
    }


}
