package com.commoncoder.scalable_chat.service.impl;

import com.commoncoder.scalable_chat.model.ServerMetadata;
import com.commoncoder.scalable_chat.service.IdGeneratorService;
import io.hypersistence.tsid.TSID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

/**
 * Implementation of IdGeneratorService using Hypersistence TSID. TSID provides 64-bit,
 * time-sortable, globally unique IDs.
 */
@Service
public class TsidIdGeneratorService implements IdGeneratorService {

  private final TSID.Factory tsidFactory;

  public TsidIdGeneratorService(ServerMetadata serverMetadata) {
    // TSID.Factory allows us to specify a node ID (worker ID).
    // TSID uses 10 bits for nodeId by default (0-1023).
    this.tsidFactory =
        TSID.Factory.builder()
            .withNode(serverMetadata.getNodeId())
            .withRandom(ThreadLocalRandom.current())
            .build();
  }

  @Override
  public long nextId() {
    return tsidFactory.generate().toLong();
  }
}
