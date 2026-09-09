pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh './mvnw clean test'
            }
        }

        stage('Package') {
            steps {
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Docker Build') {
    steps {
        sh 'docker build -t registry.sokhin.site/docker-hosted/taskpluse-api:${BUILD_NUMBER} .'
    }
}

stage('Docker Push') {
    steps {
        withCredentials([
            usernamePassword(
                credentialsId: 'nexus-registry',
                usernameVariable: 'NEXUS_USERNAME',
                passwordVariable: 'NEXUS_PASSWORD'
            )
        ]) {
            sh '''
                echo "$NEXUS_PASSWORD" | docker login registry.sokhin.site \
                    -u "$NEXUS_USERNAME" \
                    --password-stdin

                docker push registry.sokhin.site/docker-hosted/taskpluse-api:${BUILD_NUMBER}

                docker logout registry.sokhin.site
            '''
        }
    }
}
stage('Deploy to Production') {
    steps {
        sh '''
            ssh -i /var/jenkins_home/.ssh/id_ed25519 \
                -o StrictHostKeyChecking=no \
                root@64.177.41.133 \
                "
                set -e

                echo '=== Pulling image ==='
                docker pull registry.sokhin.site/docker-hosted/taskpluse-api:${BUILD_NUMBER}

                echo '=== Stopping old container ==='
                docker stop taskpluse-api || true
                docker rm taskpluse-api || true

                echo '=== Starting new container ==='
                docker run -d \
                    --name taskpluse-api \
                    --restart unless-stopped \
                    --env-file /root/taskpluse-api.env \
                    --network taskpluse-network \
                    -p 127.0.0.1:8082:8082 \
                    registry.sokhin.site/docker-hosted/taskpluse-api:${BUILD_NUMBER}

                echo '=== Waiting for application ==='
                sleep 10

                echo '=== Health check ==='

                for i in 1 2 3 4 5; do
                    if curl -fsS http://127.0.0.1:8082/actuator/health; then
                        echo
                        echo '✅ Application is healthy'
                        exit 0
                    fi

                    echo 'Application not ready yet...'
                    sleep 5
                done

                echo '❌ Health check failed'
                docker logs --tail 100 taskpluse-api
                exit 1
                "
        '''
    }
}
    }

    post {
        success {
            echo '================================='
            echo '✅ TaskPluse CI SUCCESS'
            echo '================================='
        }

        failure {
            echo '================================='
            echo '❌ TaskPluse CI FAILED'
            echo '================================='
        }
    }
}