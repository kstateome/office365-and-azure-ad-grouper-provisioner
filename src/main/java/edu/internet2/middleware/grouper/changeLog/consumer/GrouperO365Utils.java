package edu.internet2.middleware.grouper.changeLog.consumer;

import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.util.GrouperUtil;

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



}
