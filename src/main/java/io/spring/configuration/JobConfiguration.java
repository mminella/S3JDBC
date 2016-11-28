/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.spring.configuration;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import io.spring.batch.DownloadingStepExecutionListener;
import io.spring.batch.Foo;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.support.MultiResourcePartitioner;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.ItemPreparedStatementSetter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.task.batch.partition.DeployerPartitionHandler;
import org.springframework.cloud.task.batch.partition.DeployerStepExecutionHandler;
import org.springframework.cloud.task.batch.partition.NoOpEnvironmentVariablesProvider;
import org.springframework.cloud.task.batch.partition.PassThroughCommandLineArgsProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * @author Michael Minella
 */
@Configuration
public class JobConfiguration {

	@Autowired
	private ResourcePatternResolver resourcePatternResolver;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private StepBuilderFactory stepBuilderFactory;

	@Autowired
	private JobBuilderFactory jobBuilderFactory;

	@Bean
	@Profile("!worker")
	public Partitioner partitioner() throws IOException {
		Resource[] resources =
				this.resourcePatternResolver.getResources("s3://connected-car-artifacts/inputs/*.csv");

		MultiResourcePartitioner partitioner = new MultiResourcePartitioner();
		partitioner.setResources(resources);

		return partitioner;
	}

	@Bean
	@Profile("!worker")
	public DeployerPartitionHandler partitionHandler(TaskLauncher taskLauncher,
			JobExplorer jobExplorer) {
		DeployerPartitionHandler partitionHandler =
				new DeployerPartitionHandler(taskLauncher,
						jobExplorer,
						context.getResource("file:///Users/mminella/.m2/repository/io/spring/s3jdbc/0.0.1-SNAPSHOT/s3jdbc-0.0.1-SNAPSHOT.jar"),
						"load");

		List<String> commandLineArgs = new ArrayList<>(3);
		commandLineArgs.add("--spring.profiles.active=worker");
		commandLineArgs.add("--spring.cloud.task.initialize.enable=false");
		commandLineArgs.add("--spring.batch.initializer.enabled=false");
		commandLineArgs.add("--spring.datasource.initialize=false");
		partitionHandler.setCommandLineArgsProvider(new PassThroughCommandLineArgsProvider(commandLineArgs));
		partitionHandler.setEnvironmentVariablesProvider(new NoOpEnvironmentVariablesProvider());
		partitionHandler.setMaxWorkers(1);
		partitionHandler.setApplicationName("S3LoaderJob");

		return partitionHandler;
	}

	@Bean
	@Profile("worker")
	public DeployerStepExecutionHandler stepExecutionHandler(JobExplorer jobExplorer, JobRepository jobRepository) {
		return new DeployerStepExecutionHandler(this.context, jobExplorer, jobRepository);
	}

	@Bean
	@StepScope
	@Profile("worker")
	public DownloadingStepExecutionListener downloadingStepExecutionListener() {
		return new DownloadingStepExecutionListener();
	}

	@Bean
	@StepScope
	@Profile("worker")
	public FlatFileItemReader<Foo> reader(@Value("#{stepExecutionContext['localFile']}")String fileName) throws Exception {
		FlatFileItemReader<Foo> reader = new FlatFileItemReaderBuilder<Foo>()
				.name("fooReader")
				.resource(new FileSystemResource(fileName))
				.delimited()
				.names(new String[] {"first", "second", "third"})
				.targetType(Foo.class)
				.build();

		return reader;
	}

	@Bean
	@Profile("worker")
	public JdbcBatchItemWriter<Foo> writer(DataSource dataSource) {
		JdbcBatchItemWriter<Foo> writer = new JdbcBatchItemWriter<>();

		writer.setDataSource(dataSource);
		writer.setSql("INSERT INTO FOO VALUES (?, ?, ?)");
		writer.setItemPreparedStatementSetter((foo, preparedStatement) -> {
			preparedStatement.setInt(1, foo.getFirst());
			preparedStatement.setInt(2, foo.getSecond());
			preparedStatement.setString(3, foo.getThird());
		});

		return writer;
	}

	@Bean
	@Profile("worker")
	public Step load(ItemReader<Foo> reader, ItemWriter<Foo> writer, StepExecutionListener listener) {
		return this.stepBuilderFactory.get("load")
				.<Foo, Foo>chunk(20)
				.reader(reader)
				.writer(writer)
				.listener(listener)
				.build();
	}

	@Bean
	@Profile("!worker")
	public Step master(Partitioner partitioner, PartitionHandler partitionHandler) {
		return this.stepBuilderFactory.get("master")
				.partitioner("load", partitioner)
				.partitionHandler(partitionHandler)
				.build();
	}

	@Bean
	@Profile("!worker")
	public Job job() {
		return this.jobBuilderFactory.get("s3jdbc")
				.start(master(null, null))
				.build();
	}
}
