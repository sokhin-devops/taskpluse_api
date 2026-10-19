// TaskPluse API - CI pipeline.
// The Jenkins job 'taskpluse-api' is defined in taskpluse_deploy/infra/jenkins/casc.yaml
// and checks GitHub for new commits every 5 minutes.
//
//   Checkout -> Gitleaks -> Unit tests -> Dependency scan -> Build image
//            -> Image scan -> Push to Nexus
//
// Shell steps use single quotes so values like the Nexus password are expanded
// by the shell from the environment, never pasted into the script by Groovy -
// that keeps them out of the build log.
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        REGISTRY = 'localhost:5001'
        IMAGE    = 'localhost:5001/taskpluse-api'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    // Build number + commit: unique, sortable, and says which code is inside.
                    env.TAG = "${env.BUILD_NUMBER}-${env.GIT_SHORT}"
                }
                echo "Building ${env.IMAGE}:${env.TAG}"
            }
        }

        stage('Secrets scan (Gitleaks)') {
            steps {
                // The whole git history, not just the latest files: a password
                // committed once and deleted later is still in the history.
                sh 'gitleaks git --no-banner --redact --exit-code 1 .'
            }
        }

        stage('Unit tests') {
            steps {
                // The Dockerfile's test stage: same Maven and JDK as the image.
                sh 'docker build --target test --progress=plain -t taskpluse-api-test:${TAG} .'
            }
        }

        stage('Dependency scan (Trivy)') {
            steps {
                // The dependency jars inside the packaged application (from the
                // test build), not pom.xml: reading a pom needs Maven Central for
                // every parent POM, and Maven Central rate-limits that (429).
                // Maven's own jars and its download cache are skipped - only what
                // ships counts. Report HIGH and CRITICAL; fail only on CRITICAL
                // that has a fix available - something we can actually act on.
                sh '''
                    SCAN="--pkg-types library --skip-dirs /usr/share/maven --skip-dirs /root/.m2 --no-progress"
                    trivy image $SCAN --severity HIGH,CRITICAL taskpluse-api-test:${TAG}
                    trivy image $SCAN --severity CRITICAL --ignore-unfixed --exit-code 1 --quiet taskpluse-api-test:${TAG}
                '''
            }
        }

        stage('Build image') {
            steps {
                sh '''
                    docker build --progress=plain \
                        --build-arg VERSION=${TAG} \
                        --build-arg GIT_SHA=${GIT_SHORT} \
                        --build-arg BUILD_NUMBER=${BUILD_NUMBER} \
                        -t ${IMAGE}:${TAG} .
                '''
            }
        }

        stage('Image scan (Trivy)') {
            steps {
                // The whole image: OS packages of the base image too, not just our jar.
                sh '''
                    trivy image --severity HIGH,CRITICAL --no-progress ${IMAGE}:${TAG}
                    trivy image --severity CRITICAL --ignore-unfixed --exit-code 1 --no-progress --quiet ${IMAGE}:${TAG}
                '''
            }
        }

        stage('Push to Nexus') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-push',
                                                  usernameVariable: 'NEXUS_USER',
                                                  passwordVariable: 'NEXUS_PASSWORD')]) {
                    sh '''
                        echo "$NEXUS_PASSWORD" | docker login ${REGISTRY} -u "$NEXUS_USER" --password-stdin
                        docker push ${IMAGE}:${TAG}
                        docker logout ${REGISTRY}
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "Pushed ${env.IMAGE}:${env.TAG}"
        }
        always {
            // Images live in Nexus now; don't let them pile up on this PC.
            sh 'docker rmi ${IMAGE}:${TAG} taskpluse-api-test:${TAG} >/dev/null 2>&1 || true'
            cleanWs()
        }
    }
}
