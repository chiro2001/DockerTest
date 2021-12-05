FROM mozilla/sbt:8u292_1.5.4

COPY . /working/

WORKDIR /working/

CMD sbt test

