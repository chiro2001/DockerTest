FROM mozilla/sbt:8u292_1.5.4

COPY *.* /working/
COPY src /working/
COPY project /working/
COPY entrypoint.sh /

WORKDIR /working/

ENTRYPOINT ["/entrypoint.sh"]

