package com.jelly.cinema.service.ai.rag;

import com.jelly.cinema.model.vo.AiCitationVO;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RagQaContext {

    private String retrievalMode;

    private String context;

    private List<AiCitationVO> citations;
}
