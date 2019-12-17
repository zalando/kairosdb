package org.kairosdb.datastore.cassandra;

import com.datastax.driver.core.ConsistencyLevel;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.codehaus.jackson.JsonNode;
import org.kairosdb.core.onlineconfig.EntityResolver;
import org.kairosdb.core.scheduler.KairosDBJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.quartz.TriggerBuilder.newTrigger;

public class ConsistencyConfigurationUpdateJob implements KairosDBJob {
    public static final Logger logger = LoggerFactory.getLogger(ConsistencyConfigurationUpdateJob.class);
    private static final String ENTITY_ID = "kairosdb-consistency";

    private final CassandraConsistencyLevelConfiguration config;
    private final EntityResolver entityResolver;
    private final String schedule;

    @Inject
    public ConsistencyConfigurationUpdateJob(final CassandraConsistencyLevelConfiguration config,
                                                final EntityResolver entityResolver,
                                                @Named("kairosdb.cassandra.consistency.schedule") final String schedule) {
        this.config = config;
        this.schedule = schedule;
        this.entityResolver = entityResolver;
    }

    @Override
    public Trigger getTrigger() {
        return (newTrigger()
                .withIdentity(this.getClass().getSimpleName())
                .withSchedule(CronScheduleBuilder.cronSchedule(schedule))
                .build());
    }

    @Override
    public void execute(final JobExecutionContext ctx) {
        logger.debug("Updating KairosDB consistency levelconfiguration");
        logger.debug("Current config is: " + config.toString());

        Optional<JsonNode> maybeData = this.entityResolver.getEntityData(ENTITY_ID);
        maybeData.flatMap(dataNode -> this.entityResolver.getStringValue(dataNode, "read_level"))
                .map(level -> {
                    try{
                        return ConsistencyLevel.valueOf(level);
                    } catch(IllegalArgumentException ex) {
                        logger.error("Invalid read consistency level provided: " + level);
                        return null;
                    }
                })
                .ifPresent(readLevel -> {
                    logger.debug("Updating 'read_level' value to " + readLevel.name());
                    config.setReadLevel(readLevel);
                });
        logger.debug("Config updated: " + config.toString());
    }
}

