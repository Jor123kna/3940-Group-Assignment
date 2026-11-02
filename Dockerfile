#downloads an image containing java 17
FROM tomcat:10.1-jdk17 

#copies project into tomcats root directory
COPY . /usr/local/tomcat/webapps/ROOT/

#just says we are using port 8080 i actually dont know which port we are using so ill change this later
#ACTUASLLY it seems to be 8080 cause its working like this
EXPOSE 8080