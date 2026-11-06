FROM gradle:jdk21-alpine@sha256:26707fc76fb68e6c69b7aedfb49a42c6639351fc54939a81d7753e573d486b95 AS builder

COPY . /project

RUN cd /project && ./gradlew build --no-daemon

FROM bellsoft/liberica-runtime-container:jre-slim@sha256:e92d36dcfaa33e2f6cf602d9b0a1c7ab619b2ea0c15158911aef0e6cab582d47 AS runner

RUN mkdir -p /app && mkdir -p /db

VOLUME /db

# Required at runtime (pass via `docker run -e`): BOT__ADMIN, BOT__TOKEN, DATABASE__LOCATION

COPY --from=builder /project/build/libs/topics-bot-0.2.0-all.jar /app/app.jar

CMD java -jar /app/app.jar

