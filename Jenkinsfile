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
  stage('Docker Build') {
      agent any
      steps {
      sh 'docker build -t docker030303/sendx:latest .'   
      }
    }
       
      
         stage('Docker container stop'){
	  agent any
		  steps {
	         sh 'docker ps -a -q  --filter ancestor=docker030303/sendx'
      sh 'docker stop $(docker ps -q --filter ancestor=docker030303/sendx)'
      sh 'docker rm $(docker ps -a -q -f ancestor=docker030303/sendx)'

   
     
      }
    }
  }
}
