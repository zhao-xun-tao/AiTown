-- AI Town 长期记忆流（Generative Agents memory stream）
CREATE TABLE IF NOT EXISTS agent_memory (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_id        VARCHAR(64)  NOT NULL COMMENT 'Agent 唯一 ID',
    agent_name      VARCHAR(64)  NOT NULL COMMENT 'Agent 显示名',
    memory_type     VARCHAR(32)  NOT NULL COMMENT 'OBSERVATION / DIALOGUE / REFLECTION',
    content         TEXT         NOT NULL COMMENT '记忆内容',
    importance      INT          NOT NULL DEFAULT 5 COMMENT '重要性 1-10',
    game_day        INT          NOT NULL DEFAULT 1,
    game_hour       INT          NOT NULL DEFAULT 0,
    game_minute     INT          NOT NULL DEFAULT 0,
    game_tick       BIGINT       NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at DATETIME    NULL,
    INDEX idx_agent_tick (agent_id, game_tick DESC),
    INDEX idx_agent_type (agent_id, memory_type),
    INDEX idx_agent_created (agent_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 长期记忆流';
