# MyProgect
Локальный поисковый движок для индексации и поиска по веб-сайтам. Приложение сканирует заданные сайты, индексирует их содержимое и предоставляет полнотекстовый поиск с морфологическим анализом русского языка.


- ### Предварительные требования

- Java 17 или выше
- MySQL 8.0 или выше
- Maven 3.6+
- Git

### Установка и запуск

**Клонируйте репозиторий**

git clone [[https://github.com/Daiskii12/search-engine.git](https://github.com/Daiskii12/MyProgect.git)](https://github.com/Daiskii12/MyProgect/tree/main/MyProg)

cd search-engine

**Создайте файл src/main/resources/application.yaml**

server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/search_engine?useSSL=false&allowPublicKeyRetrieval=true
    username: username
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect

indexing-settings:
  user-agent: "SearchEngineBot/1.0"
  referrer: "http://www.google.com"
  delay-between-requests: 1000
  sites:
    - url: "https://www.playback.ru/"
      name: "PlayBack.Ru"
    - url: "https://volochek.life/"
      name: "Volochek"
    - url: "http://radiomv.ru/"
      name: "Radio MV"

**Создайте и настройте базу данных**
