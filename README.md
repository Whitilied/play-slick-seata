## This project is POC for, playframework with seata.

**Use distributed transaction services under a microservices architecture**

### Quick Start

* start infrastructure(mysql, seata-server and maxwell), by docker compose

```bash
docker-compose up -d
```

* prepare mysql DML

entry mysql cli (password is empty) 

```bash
docker exec -it mysql mysql -uroot

```

exec sql script in `play-slick-seata-sample/conf/sql/init_db.sql`


* watch mysql binlog by maxwell

```bash
docker logs -f maxwell
```


* start play sample program

```bash
sbt "project play-slick-seata-sample" run
```


* test success request

```bash
curl -X POST \
  http://localhost:9000/order/placeOrder \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": 1,
    "productId": 1,
    "price": 1
}'
```

* test illegal request (user balance not enough)

```bash
curl -X POST \
  http://localhost:9000/order/placeOrder \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": 1,
    "productId": 1,
    "price": 100
}'
```

### Others

[About Seata](https://seata.io/)

