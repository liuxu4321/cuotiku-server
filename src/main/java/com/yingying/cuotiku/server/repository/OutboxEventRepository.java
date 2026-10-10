package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

}
