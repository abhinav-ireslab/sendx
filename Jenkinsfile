pipeline {
  agent none
  stages {
    stage('Maven Install') {
      agent {
        docker {
          image 'maven:3.5.0'
        }
      }
      steps {
		echo 'Making build.'
		sh 'mvn clean install'
      }
    }
	  stage('Docker running container Remove'){
	  agent any
	  steps {
	       sh 'docker ps -a -q  --filter ancestor=docker030303/sendx'
               sh 'docker stop $(docker ps -q --filter ancestor=docker030303/sendx)'
               sh 'docker rm $(docker ps -a -q -f ancestor=docker030303/sendx)'
  }
}
	  
         stage('Docker Build') {
         agent any
         steps {
        	sh 'docker build -t docker030303/sendx:1.0.1 .'   
    }
 }
	stage('Docker push') {
        agent any
        steps {
       		sh 'docker login -u docker030303 -p Abhinav@123Ires'
       		sh 'docker push docker030303/sendx'
      }
    }
	stage('Run container') {
        agent any
        steps {
       		sh 'docker run -d -p 192.168.1.29:8085:8085 -t docker030303/sendx'
      }
    }
	  	  
	stage('Remove unused Image') {
        agent any
        steps {
       		sh 'docker rmi $(docker images -f "dangling=true" -q)'
      }
    }
  }
}
