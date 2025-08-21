# 黑马点评项目

## 启动项目
1.启动redis（E:\Redis\Redis-x64-5.0.14.1）
2.启动kafka文件夹中的zookeeper，再启动kafka（D:\找实习\kafka_2.12-3.5.1）
3.启动nginx代理服务
4.关于调gpt输出乱码的情况，已经通过设置将jvm的编码设置为UTF-8
控制台的默认采用GBK编码，尝试使用powershell命令：
 chcp 65001; 
 [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
 再 mvn -q -DskipTests spring-boot:run或者 mvn spring-boot:run

 5.docker start redis-vector使用docker连接向量数据库，该数据库没有设置密码。
 于是将原来的项目中的application.yaml的redis的密码设置注释了
 同时src\main\java\com\hmdp\config\RedissonConfig.java 注释了密码

 6.对于RAG Chat,resources文件夹下给了两个测试文件，因为第一次启动已经将数据向量化到了redisearch中，所以把src/main/java/com/hmdp/config/ConsultantConfig.java下的store()方法的bean注解注释掉了

 7.agent.html是nginx文件夹下的，不是resource下的，启动nginx代理后，localhost:8080/agent.html打开即可

 8.chatController提供了三种调用方式，                    
 chat   Stream  chatWitchProtocol
 对应
 /      /stream     /chatWithProtocol
 修改agent.html308行：
 const response = await fetch(`/api/chat/chatWithProtocol?……)
