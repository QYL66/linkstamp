CREATE TABLE IF NOT EXISTS `t_link_stats_event`
(
    `stream_id` varchar(64) NOT NULL,
    PRIMARY KEY (`stream_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @linkstamp_index_ddl = (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_link_user_time ON t_link_access_logs (full_short_url, user, create_time)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_link_access_logs' AND index_name = 'idx_link_user_time'
);
PREPARE linkstamp_index_statement FROM @linkstamp_index_ddl;
EXECUTE linkstamp_index_statement;
DEALLOCATE PREPARE linkstamp_index_statement;

SET @linkstamp_index_ddl = (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_link_ip_time ON t_link_access_logs (full_short_url, ip, create_time)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_link_access_logs' AND index_name = 'idx_link_ip_time'
);
PREPARE linkstamp_index_statement FROM @linkstamp_index_ddl;
EXECUTE linkstamp_index_statement;
DEALLOCATE PREPARE linkstamp_index_statement;
