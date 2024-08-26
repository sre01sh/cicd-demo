properties([
    parameters([
        [$class: "CascadeChoiceParameter", 
            choiceType: "PT_CHECKBOX", 
            filterable: false, 
            name: "Servers", 
            referencedParameters: "Environment", 
            script: [
                $class: "GroovyScript", 
                script: [
                    sandbox: true, 
                    script: 
                        ''' if (Environment.contains("qa")){
                                return["mlxdg1vlqaims01:selected"]
                            }
                            else if(Environment.contains("prod")){
                                return["mlxdg1vlpaims01:selected","mlxdg1vlpaims02:selected","mlxdg1vlpaims03:selected"]
                            }
                        '''
                ]
            ]
        ]
    ])
])

pipeline {
    agent none
    environment {
                SITE = 'dg1#'
                JENKINS_ID =  '1000:1000'
                LOG_DURATION = 10
                QA_VMOPTIONS = "\"-Xms1g -Xmx2g\""
                PROD_VMOPTIONS = "\"-Xms2g -Xmx4g\""
                // JENKINS_ID =  '55080:55081'
            }
parameters {
        choice(name: 'Environment', choices: ['qa', 'prod'])
    }
    stages {
        stage("Checkout and build") {
            agent {
                docker {
                    image 'maven:3.9.9-eclipse-temurin-8-alpine'
                    args '-u 0:0 -v maven-repo:/root/.m2'
                }
            }
            stages {
				stage('Check Permission') {
					steps {
						script {
							if( params.Environment.contains('prod') && !currentUserGlobalRoles().contains('admin')){
								throw new Exception("没有权限部署至生产环境")
							}
						}
					}
				}
				stage('Checkout') {
					steps {
					    echo "projectName: ${env.JOB_BASE_NAME}"
						// git branch: 'main', url: "https://kochsource.io/molex-dg/${env.JOB_BASE_NAME}.git", credentialsId: 'gitlabf'
                        git branch: 'main', url: "https://github.com/sre01sh/cicd-demo.git"
                        
					}
				}
				stage('Build') {
                    steps {
                        script {
                        sh 'mvn -DskipTests clean package '
                        sh 'chown -R $JENKINS_ID ./target'
                        }
                    }
        }
            }
        }
        
        
        
         stage("Deploy and Clean") {
            agent {
                docker {
                    image 'litmuschaos/ansible-runner'
                    args '-u 0:0 -v ansible-repo:/etc/ansible'
                }
            }
            stages {
				stage('Deploy') {
					steps {
						script {
						def pom = readMavenPom file: 'pom.xml'
                        def version = pom.version
                        def artifactId = pom.artifactId
                        def jarFile = artifactId + "-" + version + ".jar"
                        print(artifactId)
                        print(version)
                        print(jarFile)
                        def filePath = sh(script: 'pwd', returnStdout: true).trim()
                        sh "ls -lrht ${filePath}/target/${jarFile}"
                        def absPathFile="${filePath}/target/${jarFile}"
					    sh "ansible ${params.Servers} -m ping"
					    def options = QA_VMOPTIONS
					    if (Environment.equals("prod")){
                               options = PROD_VMOPTIONS
                            }
					    sh "ansible-playbook /etc/ansible/dg1_springboot.yml -e buildno=${env.BUILD_NUMBER} -e logduration=$LOG_DURATION -e 'servers=${params.Servers}' -e 'env=$SITE+${params.Environment}' -e 'version=${version}' -e 'artifactId=${artifactId}' -e 'absPathFile=${absPathFile}' -e 'jarFile=${jarFile}' -e 'options=${options}'"
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

    }
}