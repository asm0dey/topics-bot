FROM gradle:jdk21-alpine@sha256:ecd2dc227c1a75522d320599a18aedc5dbf9e8371324059d484d3dca21ea8230 AS builder

COPY . /project

RUN cd /project && ./gradlew build --no-daemon

FROM bellsoft/liberica-runtime-container:jre-slim@sha256:95d027e08a0fee323dd257dd56ac097fc02764edd155ca2b697943c962584135 AS runner

RUN mkdir -p /app && mkdir -p /db

VOLUME /db

# Required at runtime (pass via `docker run -e`): BOT__ADMIN, BOT__TOKEN, DATABASE__LOCATION

COPY --from=builder /project/build/libs/topics-bot-0.2.0-all.jar /app/app.jar

CMD java -jar /app/app.jar

