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
        this.subdomainStem = getSubdomainStem();

    }

    protected String getSubdomainStem() {
        return GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired("grouperO365.subdomainStem");
    }

    @Override
    public User getUserFromMs(Subject subject, String defaultDomain) {
        logger.debug("calling getUserFrom UserLookupAcrossMultipleDomains");
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
        Stem stem = StemFinder.findByName(grouperSession, subdomainStem.trim(), false);
        Set<Stem> childStems = stem.getChildStems();
        for (Stem child : childStems) {
            for (Object childGroupObject : child.getChildGroups()) {
                Group childGroup = (Group) childGroupObject;
                if (childGroup.hasMember(subject)) {
                    logger.debug("domain = " + childGroup.getName());
                    String domain = childGroup.getName();
                    domain = reduceDomain(domain);
                    possibleDomains.add(domain);
                }
            }
        }
        return possibleDomains;
    }

    String reduceDomain(String domain) {
        String[] domainData = domain.split("[:]");
        domain = domainData[domainData.length - 1];
        return domain;
    }

    private User getUserFromMultipleDomains(Subject subject, String defaultTenantId) {

        User user = apiClient.getUserFromMs(subject, defaultTenantId.trim());
        if(user == null){
            logger.debug("user was null");
        }else {
            logger.debug("user was not null...  this should be the return value..");
        }
        List<String> possibleDomains = getAccount(subject);
        User foundUser = null;
        if (!possibleDomains.isEmpty() && user == null) {
            // find ids..
            for (String domain : possibleDomains) {
                logger.debug("trying " + subject.getAttributeValue("uid") + "@" + domain.trim());
                User checkedUser = apiClient.getUserFromMs(subject, domain.trim());
                if (checkedUser != null) {
                    logger.debug("user was found" + checkedUser.userPrincipalName);
                    foundUser = checkedUser;
                }
            }
        }
        if (foundUser != null) {
            logger.debug("found user was not null");
            user = foundUser;
        }
        return user;
    }

}
