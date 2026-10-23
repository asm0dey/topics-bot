FROM gradle:jdk21-alpine@sha256:4bafff1010cd00091eed9c89e0373137ff9030f33ff80de0ad9c4001a9a22027 AS builder

COPY . /project

RUN cd /project && ./gradlew build --no-daemon

FROM bellsoft/liberica-runtime-container:jre-slim@sha256:12b1c2c044fe633ad7c6f1cc8ecfdaf5a5d70bc1248d484c460dad8e0a21c63b AS runner

RUN mkdir -p /app && mkdir -p /db

VOLUME /db

# Required at runtime (pass via `docker run -e`): BOT__ADMIN, BOT__TOKEN, DATABASE__LOCATION

COPY --from=builder /project/build/libs/topics-bot-0.2.0-all.jar /app/app.jar

CMD java -jar /app/app.jar

