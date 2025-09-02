package com.example.backend.schedule.service;

import com.example.backend.scheduleItem.entity.ScheduleItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key}")
    private String openAiApiKey;

    public Mono<String> getOptimizedRouteJson(UUID scheduleId, LocalDate startDate, LocalDate endDate, List<ScheduleItem> items) {
        log.info("🚀 AI 경로 최적화 시작 - Schedule ID: {}", scheduleId);

        String prompt = createOptimizationPrompt(scheduleId, startDate, endDate, items);
        log.debug("🤖 생성된 프롬프트: \n{}", prompt);

        Map<String, Object> requestBody = Map.of(
                "model", "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "response_format", Map.of("type", "json_object")
        );

        try {
            log.debug("➡️ OpenAI API 요청 본문: {}", objectMapper.writeValueAsString(requestBody));
        } catch (JsonProcessingException e) {
            log.warn("API 요청 본문 JSON 변환 실패 (로깅 목적)", e);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(openAiApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .headers(h -> h.addAll(headers))
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnNext(response -> {
                    try {
                        log.debug("⬅️ OpenAI API 원본 응답: {}", objectMapper.writeValueAsString(response));
                    } catch (JsonProcessingException e) {
                        log.warn("API 응답 JSON 변환 실패 (로깅 목적)", e);
                    }
                })
                .map(this::extractContentFromApiResponse)
                .doOnSuccess(content -> log.info("✅ AI 경로 최적화 응답 처리 성공 - Schedule ID: {}", scheduleId))
                .onErrorMap(throwable -> {
                    log.error("❌ OpenAI API 호출 중 심각한 오류 발생 - Schedule ID: {}", scheduleId, throwable);
                    return new RuntimeException("OpenAI API 호출 실패: " + throwable.getMessage(), throwable);
                });
    }

    private String createOptimizationPrompt(UUID scheduleId, LocalDate startDate, LocalDate endDate, List<ScheduleItem> items) {
        log.info("프롬프트 생성을 시작합니다...");
        List<ScheduleItemInfo> itemInfos = items.stream()
                .map(item -> new ScheduleItemInfo(item.getContentId()))
                .collect(Collectors.toList());

        String itemsJson;
        try {
            itemsJson = objectMapper.writeValueAsString(itemInfos);
            log.debug("직렬화된 스케줄 아이템 JSON: {}", itemsJson);
        } catch (JsonProcessingException e) {
            log.error("스케줄 아이템 JSON 직렬화 실패", e);
            throw new RuntimeException("JSON 직렬화에 실패했습니다.", e);
        }

        return String.format("""
            너는 여행 경로 최적화 전문가야.
            아래 정보를 참고해서 일정 아이템들의 효율적인 '순서(order)'를 정렬하고, 각 아이템이 어느 '몇일차(dayNumber)'에 속하는지 할당하고, 적절한 'start_time'과 'end_time'도 할당해줘.
            시간은 24시간 형식(HH:mm)으로 반환해줘.
            여행 기간은 %s 부터 %s 까지야.
            
            📌 스케줄 ID: %s
            📌 현재 스케줄 아이템 리스트:
            %s
            
            아래 JSON 구조를 절대 변경하지 말고, 다른 필드는 절대 넣지 말고,
            정확하게 아래의 JSON 형식으로만 출력해줘:
            {
              "scheduleId": "%s",
              "ScheduleItems": [
                {
                  "order": 1,
                  "contentId": "장소의 content_id",
                  "dayNumber": 1,
                  "start_time": "10:00",
                  "end_time": "12:00"
                }
              ]
            }
            
            ❗️ 다른 설명이나 말은 절대 추가하지 말고, 오직 JSON 객체만 출력해.
            """,
                startDate,
                endDate,
                scheduleId,
                itemsJson,
                scheduleId
        );
    }

    private String extractContentFromApiResponse(Map<String, Object> apiResponse) {
        log.info("API 응답에서 content 추출을 시작합니다...");
        List<Map<String, Object>> choices = (List<Map<String, Object>>) apiResponse.get("choices");
        if (choices == null || choices.isEmpty()) {
            log.error("API 응답 오류: 'choices' 필드가 없거나 비어있습니다. 응답: {}", apiResponse);
            throw new RuntimeException("OpenAI 응답에 'choices'가 없습니다.");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            log.error("API 응답 오류: 'message' 필드가 없습니다. 응답: {}", apiResponse);
            throw new RuntimeException("OpenAI 응답에 'message'가 없습니다.");
        }
        String content = (String) message.get("content");
        if (content == null || content.isBlank()) {
            log.error("API 응답 오류: 'content' 필드가 비어있습니다. 응답: {}", apiResponse);
            throw new RuntimeException("OpenAI 응답에 'content'가 비어있습니다.");
        }
        log.debug("추출된 content: {}", content);
        log.info("content 추출을 완료했습니다.");
        return content;
    }

    private record ScheduleItemInfo(String contentId) {}
}