FROM registry.opensource.zalan.do/stups/openjdk:8-42

EXPOSE 8080

RUN mkdir -p /app/conf/logging
WORKDIR /app

ADD target/kairosdb-1.3-SNAPSHOT-distribution.tar.gz /app/
ADD private-libs/tracing-lightstep-guice-starter-*.jar /app/lib/
COPY logback.xml /app/conf/logging
COPY conf/kairosdb.properties /app/conf/kairosdb.properties
COPY conf/Tracer.properties /app/conf/Tracer.properties

COPY target/scm-source.json /

CMD ["/app/bin/kairosdb.sh", "run"]
