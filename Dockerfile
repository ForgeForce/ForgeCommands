# Container image that runs your code
FROM eclipse-temurin:17

RUN mkdir /opt/app
COPY dist/app.jar /opt/app

CMD ["java", "-jar", "/opt/app/app.jar"]