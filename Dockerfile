FROM gradle:jdk21-alpine@sha256:0d0f3f4b22be94fbe1a78186c81ebb01436ffe4a0b9715889509076590f8dd46 AS builder

COPY . /project

RUN cd /project && ./gradlew build --no-daemon

FROM bellsoft/liberica-runtime-container:jre-slim@sha256:e92d36dcfaa33e2f6cf602d9b0a1c7ab619b2ea0c15158911aef0e6cab582d47 AS runner

RUN mkdir -p /app && mkdir -p /db

VOLUME /db

# Required at runtime (pass via `docker run -e`): BOT__ADMIN, BOT__TOKEN, DATABASE__LOCATION

COPY --from=builder /project/build/libs/topics-bot-0.2.0-all.jar /app/app.jar

CMD java -jar /app/app.jar

