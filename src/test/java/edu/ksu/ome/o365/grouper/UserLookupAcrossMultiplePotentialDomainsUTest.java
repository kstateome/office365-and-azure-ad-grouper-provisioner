package edu.ksu.ome.o365.grouper;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UserLookupAcrossMultiplePotentialDomainsUTest {

    @Test
    public void testReduceDomain (){
        String domain = "ksu:NotInLdapApplications:office365:subdomains:mathtest.cc.k-state.edu:mathtest.cc.k-state.edu";
        String expected = "mathtest.cc.k-state.edu";
        UserLookupAcrossMultiplePotentialDomainsMock domains = new UserLookupAcrossMultiplePotentialDomainsMock();
        assertEquals(expected,domains.reduceDomain(domain));
    }

    public class UserLookupAcrossMultiplePotentialDomainsMock extends UserLookupAcrossMultiplePotentialDomains {
        @Override
        protected String getSubdomainStem() {
            return "dummy";
        }
    }


}
