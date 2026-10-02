FROM gradle:jdk21-alpine@sha256:ee001e696f066c524517992042c8c62474d718c9088ea08eb2307b90992afa1f AS builder

COPY . /project

RUN cd /project && ./gradlew build --no-daemon

FROM bellsoft/liberica-runtime-container:jre-slim@sha256:cdb251b6fb572802e0e89b1321fe02c9db79f15a89db16ef0d6d7d06fb3fdf7c AS runner

RUN mkdir -p /app && mkdir -p /db

VOLUME /db

# Required at runtime (pass via `docker run -e`): BOT__ADMIN, BOT__TOKEN, DATABASE__LOCATION

COPY --from=builder /project/build/libs/topics-bot-0.2.0-all.jar /app/app.jar

CMD java -jar /app/app.jar

