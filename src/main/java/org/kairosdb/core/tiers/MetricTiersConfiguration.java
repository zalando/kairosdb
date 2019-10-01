package org.kairosdb.core.tiers;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.kairosdb.core.scheduler.KairosDBJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Trigger;
import org.zalando.stups.tokens.AccessToken;

import java.io.IOException;
import java.util.*;

import static org.quartz.TriggerBuilder.newTrigger;

public class MetricTiersConfiguration {
    public class UpdateJob implements KairosDBJob {
        private final ObjectMapper objectMapper;
        private final Executor executor;
        private final AccessToken accessToken;
        private final String schedule;
        private final String hostname;

        @Inject
        public UpdateJob(final ObjectMapper objectMapper,
                         final Executor executor,
                         final AccessToken accessToken,
                         @Named("kairosdb.tiers.schedule") final String schedule,
                         @Named("zmon.hostname") final String hostname) {
            this.objectMapper = objectMapper;
            this.executor = executor;
            this.accessToken = accessToken;
            this.schedule = schedule;
            this.hostname = hostname;
        }

        @Override
        public Trigger getTrigger() {
            return (newTrigger()
                    .withIdentity(this.getClass().getSimpleName())
                    .withSchedule(CronScheduleBuilder.cronSchedule(schedule))
                    .build());
        }

        @Override
        public void execute(final JobExecutionContext ctx) throws JobExecutionException {
            try {
                final Map<String, List<Integer>> checkTiers = getCheckTiers();
                final Map<String, Integer> limitConfig = getLimitConfig();
                update(checkTiers, limitConfig);
            } catch (IOException e) {
                // TODO
            }
        }

        private Map<String, List<Integer>> getCheckTiers() throws IOException {
            final String uri = hostname + "/api/v1/entities/zmon-check-tiers";
            final Request request = Request.Get(uri).addHeader("Authorization", "Bearer " + accessToken.getToken());
            final String response = executor.execute(request).returnContent().toString();
            final JsonNode jsonNode = objectMapper.readTree(response);
            final Optional<JsonNode> data = Optional.ofNullable(jsonNode.get("data"));

            final List<Integer> criticalChecksList = getCheckList(data, "critical");
            final List<Integer> importantChecksList = getCheckList(data, "important");

            return ImmutableMap.of("critical", criticalChecksList, "important", importantChecksList);
        }

        private List<Integer> getCheckList(final Optional<JsonNode> data, final String kind) {
            final Iterator<JsonNode> checks = data.map(d -> d.get(kind)).map(JsonNode::iterator).orElse(Collections.emptyIterator());
            final List<Integer> checksList = new ArrayList<>();
            while (checks.hasNext()) {
                checksList.add(checks.next().getIntValue());
            }
            return checksList;
        }

        private Map<String, Integer> getLimitConfig() {
            return null;
        }
    }

    public int getQueryDistanceHoursLimit() {
        return 0;
    }

    public boolean isMetricActiveForQuery(final String metricName) {
        final String[] split = metricName.split("\\.");
        if (split.length != 3 || !split[0].equals("zmon") || !split[1].equals("check")) {
            return false;
        }
        int checkId;
        try {
            checkId = Integer.parseInt(split[2]);
        } catch (NumberFormatException nfe) {
            return false;
        }

        return false;
    }

    private void update(final Map<String, List<Integer>> checkTiers,
                        final Map<String, Integer> limitConfig) {
    }

}
