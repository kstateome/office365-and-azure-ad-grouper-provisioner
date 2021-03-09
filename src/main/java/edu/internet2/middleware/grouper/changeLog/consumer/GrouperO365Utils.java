package edu.internet2.middleware.grouper.changeLog.consumer;

import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import org.apache.commons.lang.StringUtils;

import java.util.Set;

public class GrouperO365Utils {

    /**
     * sources for subjects
     * @return the config sources for subjects
     */
    public static Set<String> configSourcesForSubjects() {

        //# put the comma separated list of sources to send to duo
        //grouperDuo.sourcesForSubjects = someSource
        String sourcesForSubjectsString = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired("grouperO365.sourcesForSubjects");

        return GrouperUtil.splitTrimToSet(sourcesForSubjectsString, ",");
    }

    public static String configSubjectAttributeForO365Username() {
        return GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired("grouperO365.subjectAttributeForO365Username");
    }

    public static String configUserLookupClass() {
        return GrouperLoaderConfig.retrieveConfig().propertyValueString("grouperO365.userLookupClass","edu.internet2.middleware.grouper.changeLog.consumer.Office365ApiClient");
    }

    public static String replacePrefix(String stemPrefix,String azurePrefix, String fullStem){
        if(StringUtils.isNotEmpty(fullStem) && StringUtils.isNotEmpty(stemPrefix) && StringUtils.isNotEmpty(azurePrefix)){
            return fullStem.replaceAll(stemPrefix,azurePrefix);
        }
        return fullStem;
    }

    public static String getShortGroupName(String longGroupName, int numberOfSeparators){
        if(!StringUtils.isEmpty(longGroupName) && numberOfSeparators >= 2 ){

            String[] array = longGroupName.split(":");
            String returnValue = "";
            for(int x = numberOfSeparators; x <= array.length -1; x++){
                returnValue += array[x];
                if( x < array.length-1) {
                    returnValue += ":";
                }
            }

            return returnValue;
        }
        return longGroupName;
    }




}
