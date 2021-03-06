package com.scylladb.cdc.debezium.connector;

import com.datastax.driver.core.utils.UUIDs;
import com.scylladb.cdc.model.GenerationId;
import com.scylladb.cdc.model.StreamId;
import com.scylladb.cdc.model.TaskId;
import com.scylladb.cdc.model.Timestamp;
import com.scylladb.cdc.transport.MasterTransport;
import org.apache.kafka.connect.source.SourceConnectorContext;
import org.apache.kafka.connect.storage.OffsetStorageReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;
import java.util.stream.Collectors;

public class ScyllaMasterTransport implements MasterTransport {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SourceConnectorContext context;
    private final SourceInfo sourceInfo;
    private volatile Map<TaskId, SortedSet<StreamId>> currentWorkerConfigurations;

    public ScyllaMasterTransport(SourceConnectorContext context, SourceInfo sourceInfo) {
        this.context = context;
        this.sourceInfo = sourceInfo;
    }

    @Override
    public Optional<GenerationId> getCurrentGenerationId() {
        // TODO - persist generation info - do not start from first generation
        return Optional.empty();
    }

    @Override
    public boolean areTasksFullyConsumedUntil(Set<TaskId> tasks, Timestamp until) {
        OffsetStorageReader reader = context.offsetStorageReader();

        List<Map<String, String>> partitions = tasks.stream()
                .map(sourceInfo::partition)
                .collect(Collectors.toList());

        Collection<Map<String, Object>> offsets = reader.offsets(partitions).values();

        return offsets.stream().allMatch(o -> isOffsetFullyConsumedUntil(o, until));
    }

    private boolean isOffsetFullyConsumedUntil(Map<String, Object> offset, Timestamp until) {
        if (offset == null) {
            return false;
        }
        UUID offsetUUID = UUID.fromString((String) offset.get(SourceInfo.WINDOW_START));
        Date offsetDate = new Date(UUIDs.unixTimestamp(offsetUUID));
        return offsetDate.after(until.toDate());
    }

    @Override
    public void configureWorkers(Map<TaskId, SortedSet<StreamId>> workerConfigurations) {
        this.currentWorkerConfigurations = workerConfigurations;
        context.requestTaskReconfiguration();
    }

    public Map<TaskId, SortedSet<StreamId>> getWorkerConfigurations() {
        return currentWorkerConfigurations;
    }
}
