package com.hmdp.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import com.hmdp.chat.memory.ChatMemoryProvider;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.ConsultantService;
import com.hmdp.utils.UserHolder;

/**
 * 聊天接口控制器。
 * 注意：ChatMemory 为每个用户独立，通过 provider 按需获取；不在 Controller 中缓存单个实例。
 */
@RestController
@RequestMapping("/chat")
public class chatController {

    @Resource
    private ConsultantService consultantService;

    @Resource
    private ChatMemoryProvider chatMemoryProvider;

    /**
     * 同步对话。
     */
    @GetMapping
    public ResponseEntity<String> chat(@RequestParam(required = false, name = "message") String message){
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return ResponseEntity.status(401).body("未登录");
        }
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body("消息不能为空");
        }
        return ResponseEntity.ok(consultantService.chat(message));
    }

   
    @GetMapping(value="/stream", produces="text/html;charset=UTF-8")
    public Flux<String> stream(@RequestParam String message, HttpServletResponse response) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Flux.just("未登录");
        }
        if (message == null || message.isBlank()) {
            return Flux.just("消息不能为空");
        }
        return consultantService.chatStreamFlux(message);
    }

    /**
     * 新建会话：清空当前登录用户的聊天记忆。
     */
    @PostMapping("/reset")
    public ResponseEntity<String> reset(){
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return ResponseEntity.status(401).body("未登录");
        }
        chatMemoryProvider.get(user.getId()).clear();
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/chatWithProtocol")
    public ResponseEntity<String> chatWithProtocol(@RequestParam String message) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return ResponseEntity.status(401).body("未登录");
        }
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body("消息不能为空");
        }

        String response = consultantService.chatWithProtocol(message);
        return ResponseEntity.ok(response);
    }
}
