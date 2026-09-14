FROM mysql:8

COPY docker/mysql/my.cnf /etc/mysql/conf.d/flowerconnect.cnf
COPY docker/mysql/init/00-init.sql /docker-entrypoint-initdb.d/00-init.sql

RUN chown mysql:mysql /etc/mysql/conf.d/flowerconnect.cnf /docker-entrypoint-initdb.d/00-init.sql \
    && chmod 644 /etc/mysql/conf.d/flowerconnect.cnf /docker-entrypoint-initdb.d/00-init.sql

USER mysql