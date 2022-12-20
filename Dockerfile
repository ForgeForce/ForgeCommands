# Container image that runs your code
FROM eclipse-temurin:17

COPY dist/app.jar /app.jar

COPY run.sh /run.sh
RUN chmod +x /run

ENTRYPOINT /bin/bash /run-scripts.sh