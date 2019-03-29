package edu.ksu.ome.o365.grouper;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.Stem;
import edu.internet2.middleware.grouper.StemFinder;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.changeLog.consumer.O365UserLookup;
import edu.internet2.middleware.grouper.changeLog.consumer.Office365ApiClient;
import edu.internet2.middleware.grouper.changeLog.consumer.model.User;
import edu.internet2.middleware.subject.Subject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class UserLookupAcrossMultiplePotentialDomains implements O365UserLookup {
    private static final Log logger = LogFactory.getLog(UserLookupAcrossMultiplePotentialDomains.class);

    private GrouperSession grouperSession;
    private String subdomainStem;
    private Office365ApiClient apiClient;

    public UserLookupAcrossMultiplePotentialDomains() {
        this.subdomainStem = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired("grouperO365.subdomainStem");

    }

    @Override
    public User getUser(Subject subject, String defaultDomain) {
        return getUserFromMultipleDomains(subject,defaultDomain);
    }

    @Override
    public void setApiClient(Office365ApiClient client) {
        this.apiClient = client;
        grouperSession = GrouperSession.startRootSessionIfNotStarted().getGrouperSession();
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

    private User getUserFromMultipleDomains(Subject subject, String defaultTenantId) {
        User user = apiClient.getUser(subject, defaultTenantId);
        List<String> possibleDomains = getAccount(subject);
        User foundUser = null;
        if (!possibleDomains.isEmpty() && user == null) {
            // find ids..
            for (String domain : possibleDomains) {
                logger.debug("trying " + subject.getAttributeValue("uid") + "@" + domain.trim());
                user = apiClient.getUser(subject, domain.trim());
                if (user != null) {
                    logger.debug("user was found" + user.userPrincipalName);
                    foundUser = user;
                }
            }
        }
        if (foundUser != null) {
            user = foundUser;
        }
        return user;
    }

}