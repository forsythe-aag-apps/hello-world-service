FROM azul/zulu-openjdk:8

VOLUME /tmp
ENTRYPOINT ["java","-Dspring.profiles.active=dev", "-jar","/app.jar"]
ADD ./target/hello-world-service-1.0.0-SNAPSHOT.jar /app.jar
RUN sh -c 'touch /app.jar'

EXPOSE 8080
