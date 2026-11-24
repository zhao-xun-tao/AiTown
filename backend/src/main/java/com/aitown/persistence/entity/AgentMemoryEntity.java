package com.aitown.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对应表 agent_memory —— Agent 长期记忆流。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_memory")
public class AgentMemoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String agentId;

    private String agentName;

    /** OBSERVATION / DIALOGUE / REFLECTION */
    private String memoryType;

    private String content;

    /** 重要性 1-10 */
    private Integer importance;

    private Integer gameDay;

    private Integer gameHour;

    private Integer gameMinute;

    private Long gameTick;

    private LocalDateTime createdAt;

    private LocalDateTime lastAccessedAt;
}
