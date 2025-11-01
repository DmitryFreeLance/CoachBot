FROM ubuntu:latest
LABEL authors="dmitry"

ENTRYPOINT ["top", "-b"]