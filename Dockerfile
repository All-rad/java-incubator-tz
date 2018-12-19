FROM ubuntu:18.04
MAINTAINER Myshanskii Alexandr (myshanskii.aleks@gmail.com)

RUN apt update && apt install -yqq maven default-jdk
RUN cd var && mkdir app && cd app && mkdir src

ADD src /var/app/src
ADD config.ini /var/app
ADD pom.xml /var/app

WORKDIR /var/app
RUN mvn install && mvn compile

CMD ["bash"]
