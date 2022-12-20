# Container image that runs your code
FROM eclipse-temurin:17

COPY dist/app.jar /bin/bash/app.jar

COPY run.sh /run.sh
RUN chmod +x /run.sh

ENTRYPOINT /bin/bash /run.sh