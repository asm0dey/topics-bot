FROM gradle:jdk21-graal-jammy AS builder

COPY . /project

RUN cd /project && gradle build

FROM ghcr.io/graalvm/jdk-community:22 AS runner

RUN mkdir -p /app

COPY --from=builder /project/build/libs/topics-bot-0.1.0-all.jar /app/app.jar

CMD java -jar /app/app.jar

