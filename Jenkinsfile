@Library('jenkins-shared-libs')
import edu.ksu.jenkins.*

pipeline {
    agent any
    environment {
        testDeploy = "${BRANCH_NAME}"

        regexIgnore = ".*maven-release-plugin.*;.*skipJenkins.*"

        testBranches = "test1\ntest2\ntest3"

        promptTestBranchRegex = "merge-.*"
    }

    tools {
        maven "Maven 3.5"
        jdk "Java 8"
    }

    stages {
        stage('Build') {
            agent any
            steps {
                script {
                    if (shouldIgnoreCommit(env.regexIgnore.split(';'))) {
                        error "Ignoring commit"
                    }
                }
                sh 'mvn clean package -DskipTests'
            }
            post {
                always {
                    script {
                        if (shouldIgnoreCommit(env.regexIgnore.split(';'))) {
                            currentBuild.result = 'NOT_BUILT'
                        }
                    }
                }
            }
        }

        stage('Unit Tests') {
            agent any
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
                failure {
                    itsChat Constants.DEFAULT_CHANNEL, "${env.JOB_NAME} had unit test failures on branch ${env.BRANCH_NAME} \nRecent Changes - ${getChangeString(10)}\nBuild: ${BUILD_URL}", "Unit Tests Failed"
                }
                unstable {
                    itsChat Constants.DEFAULT_CHANNEL, "${env.JOB_NAME} had unit test failures on branch ${env.BRANCH_NAME} \nRecent Changes - ${getChangeString(10)}\nBuild: ${BUILD_URL}", "Unit Tests Unstable"
                }
                changed {
                    script {
                        if (currentBuild.result == null || currentBuild.result == 'SUCCESS') {
                            itsChat Constants.BUILD_WEBHOOK_URL, "${env.JOB_NAME} now has passing unit tests on branch ${env.BRANCH_NAME} \nRecent Changes - ${getChangeString(10)}\nBuild: ${BUILD_URL}", "Unit Tests Passed"
                        }
                    }
                }
            }
        }

        stage('test-deploy') {
            agent any
            when {
                expression { return env.testDeploy  =~ /test(\d+)/ }
            }
            steps {
                sh "echo this is a jar.. nothing to deploy"
            }
        }

        stage('Sonar') {
            agent any
            when {
                branch 'master'
            }
            steps {
                sh 'mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent install'
                sh "mvn sonar:sonar -P sonar -Dsonar.branch=${env.branch_name}"
            }
        }

        stage('Maven Site') {
            agent any
            when { branch 'master' }
            steps { sh 'mvn site-deploy' }
            post {
                success {
                    itsChat Constants.BUILD_WEBHOOK_URL, "Successfully generated Maven site documentation for office365-and-azure-ad-grouper-provisioner: https://jenkins.ome.ksu.edu/maven-site/loffice365-and-azure-ad-grouper-provisioner/", "Maven Site Deployed"
                }
            }
        }

        stage('release-dry-run') {
            agent any
            when {
                branch 'release'
            }
            steps {
                sh 'mvn --batch-mode -DdryRun=true release:clean release:prepare release:perform'
                // This must be run in an agent in order to resolve the version. There is probably a better alternative that we could use in the future
                itsChat Constants.RELEASE_CONFIRM_CHANNEL, "Release Dry Run of ${JOB_NAME} ${version()} finished. Continue Release? - ${BUILD_URL}console", "Confirm Release"
            }
        }

        stage('confirm-release') {
            agent none
            when {
                branch 'release'
            }
            steps {
                input "Click continue to release ${JOB_NAME}"
            }
        }
        stage('release') {
            agent any
            when {
                branch 'release'
            }
            steps {
                sh 'mvn --batch-mode release:clean release:prepare release:perform'
            }

            post {
                success {
                    itsChat Constants.RELEASE_BUILT_NOTIFICATION_CHANNEL, "Successfully built release  ${version()}\n Build: ${BUILD_URL}", "Release Built"
                }
            }
        }
    }

    post {
        always {
            deleteDir()
        }
    }
}

@NonCPS
def version() {
    pom = readMavenPom file: 'pom.xml'
    pom.version
}

def shouldIgnoreCommit(regexIgnoreList) {
    def lastCommit = sh (script: 'git log --pretty=oneline | head -n 1', returnStdout: true)
    // For loop is used because [].each is not serializable
    for (int i = 0; i < regexIgnoreList.size(); i++) {
        if (lastCommit =~ /${regexIgnoreList[i]}/) {
            return true
        }
    }
    return false
}

@NonCPS
def getChangeString(maxMessages) {
    MAX_MSG_LEN = 100
    COMMIT_HASH_DISPLAY_LEN = 7
    def changeString = ""

    def changeLogSets = currentBuild.changeSets


    for (int i = 0; i < changeLogSets.size(); i++) {
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.length && i + j < maxMessages; j++) {
            def entry = entries[j]
            truncated_msg = entry.msg.take(MAX_MSG_LEN)
            commitHash = entry.commitId.take(COMMIT_HASH_DISPLAY_LEN)
            changeString += "${commitHash}... - ${truncated_msg} [${entry.author}]\n"
        }
    }

    if (!changeString) {
        changeString = " There have not been changes since the last build"
    }
    return changeString
}
