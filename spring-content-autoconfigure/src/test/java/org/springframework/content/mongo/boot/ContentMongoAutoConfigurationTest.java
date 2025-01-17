package org.springframework.content.mongo.boot;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.mongo.store.MongoContentStore;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.jmx.support.RegistrationPolicy;

import internal.org.springframework.content.s3.boot.autoconfigure.S3ContentAutoConfiguration;

public class ContentMongoAutoConfigurationTest {

	@Test
	public void contextLoads() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(TestConfig.class);
		context.refresh();

		MatcherAssert.assertThat(context.getBean(TestEntityContentRepository.class),
				CoreMatchers.is(CoreMatchers.not(CoreMatchers.nullValue())));

		context.close();
	}

	@Configuration
	@AutoConfigurationPackage
    @EnableAutoConfiguration(exclude=S3ContentAutoConfiguration.class)
	@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
	public static class TestConfig {
	}

	@Document
	public class TestEntity {
		@Id
		private String id;
		@ContentId
		private String contentId;
	}

	public interface TestEntityRepository extends MongoRepository<TestEntity, String> {
	}

	public interface TestEntityContentRepository extends MongoContentStore<TestEntity, String> {
	}
}
