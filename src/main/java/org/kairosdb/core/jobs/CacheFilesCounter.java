/*
 * Copyright 2013 Proofpoint Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.kairosdb.core.jobs;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.kairosdb.core.datastore.KairosDatastore;
import org.kairosdb.core.scheduler.KairosDBJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.quartz.TriggerBuilder.newTrigger;

public class CacheFilesCounter implements KairosDBJob
{
	private static final Logger logger = LoggerFactory.getLogger(CacheFilesCounter.class);
	private static final String COUNTING_SCHEDULE = "kairosdb.query_cache.cache_files_counter_schedule";

	private final KairosDatastore datastore;
	private String schedule;

	@Inject
	public CacheFilesCounter(@Named(COUNTING_SCHEDULE) String schedule, KairosDatastore datastore)
	{
		this.datastore = datastore;
		this.schedule = schedule;
		logger.warn(String.format("The schedule is '%s'", schedule));
	}

	@Override
	public void execute(JobExecutionContext jobExecutionContext)
	{
		logger.warn("Trying to count files in the cache dir");
		try {
			datastore.countFilesInTheCacheDir();
			logger.warn("Count finished");
		} catch (Exception e) {
			logger.error("Exception during counting files in the cache dir", e);
		}
	}

	@Override
	public Trigger getTrigger()
	{
		return newTrigger()
				.withIdentity(this.getClass().getSimpleName())
				.withSchedule(CronScheduleBuilder.cronSchedule(schedule))
				.build();
	}
}