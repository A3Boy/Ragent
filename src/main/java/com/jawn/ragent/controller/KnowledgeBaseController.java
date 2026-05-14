package com.jawn.ragent.controller;

import com.jawn.ragent.dto.KbCreateRequest;
import com.jawn.ragent.entity.KnowledgeBase;
import com.jawn.ragent.entity.User;
import com.jawn.ragent.repository.KnowledgeBaseRepository;
import com.jawn.ragent.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/knowledge-bases")
@Slf4j
public class KnowledgeBaseController {

    private final KnowledgeBaseRepository kbRepository;
    private final AuthService authService;

    public KnowledgeBaseController(KnowledgeBaseRepository kbRepository, AuthService authService) {
        this.kbRepository = kbRepository;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestAttribute("userId") String userId,
                                    @RequestBody KbCreateRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body(errorResponse("知识库名称不能为空"));
        }

        User user = authService.getUserById(userId);
        KnowledgeBase kb = KnowledgeBase.builder()
                .name(request.getName())
                .description(request.getDescription())
                .user(user)
                .build();
        kb = kbRepository.save(kb);

        log.info("创建知识库: {} (id: {}, user: {})", kb.getName(), kb.getId(), userId);
        return ResponseEntity.ok(toResponse(kb));
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestAttribute("userId") String userId) {
        List<KnowledgeBase> kbs = kbRepository.findByUserId(userId);
        List<Map<String, Object>> items = kbs.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("knowledgeBases", items);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{kbId}")
    public ResponseEntity<?> get(@RequestAttribute("userId") String userId,
                                 @PathVariable String kbId) {
        return kbRepository.findByIdAndUserId(kbId, userId)
                .map(kb -> ResponseEntity.ok(toResponse(kb)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{kbId}")
    public ResponseEntity<?> update(@RequestAttribute("userId") String userId,
                                    @PathVariable String kbId,
                                    @RequestBody KbCreateRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body(errorResponse("知识库名称不能为空"));
        }

        return kbRepository.findByIdAndUserId(kbId, userId)
                .map(kb -> {
                    kb.setName(request.getName());
                    kb.setDescription(request.getDescription());
                    kb = kbRepository.save(kb);
                    log.info("更新知识库: {} (id: {}, user: {})", kb.getName(), kb.getId(), userId);
                    return ResponseEntity.ok(toResponse(kb));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{kbId}")
    public ResponseEntity<?> delete(@RequestAttribute("userId") String userId,
                                    @PathVariable String kbId) {
        return kbRepository.findByIdAndUserId(kbId, userId)
                .map(kb -> {
                    kbRepository.delete(kb);
                    log.info("删除知识库: {} (id: {}, user: {})", kb.getName(), kb.getId(), userId);
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "知识库删除成功");
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toResponse(KnowledgeBase kb) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", kb.getId());
        map.put("name", kb.getName());
        map.put("description", kb.getDescription());
        map.put("createdAt", kb.getCreatedAt());
        return map;
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}
