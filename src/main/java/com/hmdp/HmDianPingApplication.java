package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.hmdp.mapper")
@SpringBootApplication
public class HmDianPingApplication {

    public static void main(String[] args) {
        String fe = System.getProperty("file.encoding");
        String dc = java.nio.charset.Charset.defaultCharset().name();
        System.out.println("[BOOT] file.encoding=" + fe + ", defaultCharset=" + dc);
        if (!"UTF-8".equalsIgnoreCase(fe) || !"UTF-8".equalsIgnoreCase(dc)) {
            System.err.println("[FATAL] 当前 JVM 默认编码不是 UTF-8 ("+fe+","+dc+")，请确认 -Dfile.encoding=UTF-8 已生效或使用 JDK 18+/21。已中止以避免中文流式继续乱码。");
            return;
        }
        SpringApplication.run(HmDianPingApplication.class, args);
    }

}
