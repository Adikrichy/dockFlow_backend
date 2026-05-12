package org.aldousdev.dockflowbackend.auth.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.entity.TelegramBinding;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.repository.TelegramBindingRepository;
import org.aldousdev.dockflowbackend.auth.service.impls.AuthServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
@Tag(name = "Telegram", description = "Управление привязкой к Telegram")
public class TelegramController {

    private final AuthServiceImpl authService;
    private final TelegramBindingRepository telegramBindingRepository;

    @Value("${telegram.bot.username:DockflowBot}")
    private String botUsername;

    @GetMapping("/status")
    @Operation(summary = "Получить статус привязки Telegram")
    public ResponseEntity<Map<String, Object>> getStatus() {
        User user = authService.getCurrentUser();
        return telegramBindingRepository.findByUserId(user.getId())
                .map(binding -> {
                    Map<String, Object> response = new java.util.HashMap<>();
                    response.put("isLinked", binding.getTelegramId() != null);
                    response.put("telegramId", binding.getTelegramId());
                    response.put("hasActiveToken", binding.getLinkingToken() != null && 
                                          binding.getTokenExpiresAt() != null &&
                                          binding.getTokenExpiresAt().isAfter(LocalDateTime.now()));
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.ok(Map.of("isLinked", false)));
    }

    @PostMapping("/link-token")
    @Operation(summary = "Сгенерировать токен для привязки бота")
    public ResponseEntity<Map<String, String>> generateLinkToken() {
        User user = authService.getCurrentUser();
        
        TelegramBinding binding = telegramBindingRepository.findByUserId(user.getId())
                .orElse(TelegramBinding.builder().user(user).build());
        
        String token = UUID.randomUUID().toString().substring(0, 8);
        binding.setLinkingToken(token);
        binding.setTokenExpiresAt(LocalDateTime.now().plusMinutes(15));
        
        telegramBindingRepository.save(binding);
        
        return ResponseEntity.ok(Map.of(
                "token", token, 
                "botUrl", "https://t.me/" + botUsername + "?start=" + token
        ));
    }

    @DeleteMapping("/unlink")
    @Operation(summary = "Отвязать Telegram")
    public ResponseEntity<Void> unlink() {
        User user = authService.getCurrentUser();
        telegramBindingRepository.findByUserId(user.getId()).ifPresent(binding -> {
            binding.setTelegramId(null);
            binding.setLinkingToken(null);
            binding.setTokenExpiresAt(null);
            telegramBindingRepository.save(binding);
        });
        return ResponseEntity.ok().build();
    }
}
