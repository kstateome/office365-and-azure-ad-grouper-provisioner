@Library('jenkins-shared-libs') _
def config = [ appName: 'office365-and-azure-ad-grouper-provisioner',
               podName: 'java-8-maven-3.5.2.yaml',
               containerName: 'jdk-8-maven',
               runUnitTests: true,
               runIntegrationTests: false,
               runMavenSite: true
             ]
javaPipeline(config)
