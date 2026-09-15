FROM gradle:jdk21-alpine@sha256:20010e663396ffee19018c1adccc4989ec49d1ff04a791630df58609d0f398fd AS builder

COPY . /project

RUN cd /project && ./gradlew build --no-daemon

FROM bellsoft/liberica-runtime-container:jre-slim@sha256:c8767c16ed098372ff85ae32a5504f585271e0e78523988ac5fa2058cd46be85 AS runner

RUN mkdir -p /app && mkdir -p /db

VOLUME /db

# Required at runtime (pass via `docker run -e`): BOT__ADMIN, BOT__TOKEN, DATABASE__LOCATION

COPY --from=builder /project/build/libs/topics-bot-0.2.0-all.jar /app/app.jar

CMD java -jar /app/app.jar

