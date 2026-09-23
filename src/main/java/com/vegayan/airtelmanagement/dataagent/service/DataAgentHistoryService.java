package com.vegayan.airtelmanagement.dataagent.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.dataagent.dto.DataAgentHistoryDto;
import com.vegayan.airtelmanagement.dataagent.repository.DataAgentHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class DataAgentHistoryService extends BaseService {

    private final DataAgentHistoryRepository historyRepository;

    public DataAgentHistoryService(DataAgentHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public List<DataAgentHistoryDto> getHistory(Long userId) {
        return historyRepository.findByUserId(userId);
    }

    public ApiResponse saveHistory(Long userId, String question, String summary, String intent, Integer rowCount) {
        return historyRepository.save(userId, question, summary, intent, rowCount);
    }

    public ApiResponse deleteHistory(Long userId, Long historyId) {
        return historyRepository.delete(userId, historyId);
    }

    public ApiResponse clearHistory(Long userId) {
        return historyRepository.clear(userId);
    }
}
