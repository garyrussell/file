/*
 * Copyright 2015-2016 the original author or authors.
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

package org.springframework.cloud.stream.app.file.source;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.integration.file.splitter.FileSplitter;
import org.springframework.integration.json.JsonPathUtils;
import org.springframework.integration.support.json.JsonObjectMapperProvider;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Gary Russell
 * @author Artem Bilan
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = FileSourceTests.FileSourceApplication.class)
@DirtiesContext
public abstract class FileSourceTests {

	private static final String TMPDIR = System.getProperty("java.io.tmpdir");

	private static final String ROOT_DIR = TMPDIR + File.separator + "dataflow-tests"
			+ File.separator + "input";

	@Autowired
	protected Source source;

	@Autowired
	protected MessageCollector messageCollector;

	protected File atomicFileCreate(String filename) throws IOException {
		File file = new File(ROOT_DIR, filename + ".tmp");
		File fileFinal = new File(ROOT_DIR, filename);
		file.delete();
		file.deleteOnExit();
		fileFinal.delete();
		fileFinal.deleteOnExit();
		FileOutputStream fos = new FileOutputStream(file);
		fos.write("this is a test\nline2\n".getBytes());
		fos.close();
		assertTrue(file.renameTo(fileFinal));
		return fileFinal;
	}

	@IntegrationTest({"file.directory = ${java.io.tmpdir}${file.separator}dataflow-tests${file.separator}input",
			"trigger.fixedDelay = 100", "trigger.timeUnit = MILLISECONDS"})
	public static class ContentPayloadTests extends FileSourceTests {

		@Test
		public void testSimpleFile() throws Exception {
			String filename = "test.txt";
			File file = atomicFileCreate(filename);
			Message<?> received = messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(byte[].class));
			assertEquals("this is a test\nline2\n", new String((byte[]) received.getPayload()));
			file.delete();
		}

	}

	@IntegrationTest({"file.directory = ${java.io.tmpdir}${file.separator}dataflow-tests${file.separator}input",
			"trigger.fixedDelay = 100", "trigger.timeUnit = MILLISECONDS", "file.consumer.mode = ref"})
	public static class FilePayloadTests extends FileSourceTests {

		@Test
		public void testSimpleFile() throws Exception {
			File file = atomicFileCreate("test.txt");
			Message<?> received = messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertEquals(file, received.getPayload());
			file.delete();
		}

	}

	@IntegrationTest({"file.directory = ${java.io.tmpdir}${file.separator}dataflow-tests${file.separator}input",
			"trigger.fixedDelay = 100", "trigger.timeUnit = MILLISECONDS", "file.consumer.mode = lines"})
	public static class LinesPayloadTests extends FileSourceTests {

		@Test
		public void testSimpleFile() throws Exception {
			File file = atomicFileCreate("test.txt");
			Message<?> received = messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(String.class));
			assertEquals("this is a test", received.getPayload());
			received = messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(String.class));
			assertEquals("line2", received.getPayload());
			file.delete();
		}

	}

	@IntegrationTest({"file.directory = ${java.io.tmpdir}${file.separator}dataflow-tests${file.separator}input",
			"trigger.fixedDelay = 100", "trigger.timeUnit = MILLISECONDS", "file.consumer.mode = lines",
			"file.consumer.withMarkers = true", "file.consumer.markersJson = false"})
	public static class LinesAndMarkersPayloadTests extends FileSourceTests {

		@Test
		public void testSimpleFile() throws Exception {
			File file = atomicFileCreate("test.txt");
			Message<?> received = messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(FileSplitter.FileMarker.class));
			assertEquals(FileSplitter.FileMarker.Mark.START, ((FileSplitter.FileMarker) received.getPayload()).getMark());
			received = messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(String.class));
			assertEquals("this is a test", received.getPayload());
			received = messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(String.class));
			assertEquals("line2", received.getPayload());
			received = messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(FileSplitter.FileMarker.class));
			assertEquals(FileSplitter.FileMarker.Mark.END, ((FileSplitter.FileMarker) received.getPayload()).getMark());
			file.delete();
		}

	}

	@IntegrationTest({"file.directory = ${java.io.tmpdir}${file.separator}dataflow-tests${file.separator}input",
			"trigger.fixedDelay = 100", "trigger.timeUnit = MILLISECONDS", "file.consumer.mode = lines",
			"file.consumer.withMarkers = true"})
	public static class LinesAndMarkersAsJsonPayloadTests extends FileSourceTests {

		@Test
		public void testSimpleFile() throws Exception {
			File file = atomicFileCreate("test.txt");
			Message<?> received = messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(String.class));
			assertEquals(FileSplitter.FileMarker.Mark.START.name(),
					JsonPathUtils.evaluate(received.getPayload(), "$.mark"));
			received = messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(String.class));
			assertEquals("this is a test", received.getPayload());
			received = messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertThat(received.getPayload(), Matchers.instanceOf(String.class));
			assertEquals("line2", received.getPayload());
			received = messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			Object fileMarker = received.getPayload();
			assertThat(fileMarker, Matchers.instanceOf(String.class));
			assertEquals(FileSplitter.FileMarker.Mark.END.name(), JsonPathUtils.evaluate(fileMarker, "$.mark"));
			FileSplitter.FileMarker fileMarker1 = JsonObjectMapperProvider.newInstance()
					.fromJson(fileMarker, FileSplitter.FileMarker.class);
			assertEquals(FileSplitter.FileMarker.Mark.END, fileMarker1.getMark());
			assertEquals(file.getAbsolutePath(), fileMarker1.getFilePath());
			assertEquals(2, fileMarker1.getLineCount());
			file.delete();
		}

	}


	@IntegrationTest({"file.directory = ${java.io.tmpdir}${file.separator}dataflow-tests${file.separator}input",
			"trigger.fixedDelay = 100", "trigger.timeUnit = MILLISECONDS", "file.consumer.mode = ref",
			"file.filenamePattern = *.txt"})
	public static class FilePayloadWithPatternTests extends FileSourceTests {

		@Test
		public void testSimpleFile() throws Exception {
			File file = atomicFileCreate("test.txt");
			File hidden = atomicFileCreate("test.foo");
			assertTrue(new File(ROOT_DIR, "test.foo").exists());
			Message<?> received = messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertEquals(file, received.getPayload());
			received = messageCollector.forChannel(source.output()).poll(300, TimeUnit.MILLISECONDS);
			assertNull(received);
			file.delete();
			hidden.delete();
		}

	}

	@IntegrationTest({"file.directory = ${java.io.tmpdir}${file.separator}dataflow-tests${file.separator}input",
			"trigger.fixedDelay = 100", "trigger.timeUnit = MILLISECONDS", "file.consumer.mode = ref",
			"file.filenameRegex = .*.txt"})
	public static class FilePayloadWithRegexTests extends FileSourceTests {

		@Test
		public void testSimpleFile() throws Exception {
			File file = atomicFileCreate("test.txt");
			File hidden = atomicFileCreate("test.foo");
			assertTrue(new File(ROOT_DIR, "test.foo").exists());
			Message<?> received = messageCollector.forChannel(source.output()).poll(10, TimeUnit.SECONDS);
			assertNotNull(received);
			assertEquals(file, received.getPayload());
			received = messageCollector.forChannel(source.output()).poll(300, TimeUnit.MILLISECONDS);
			assertNull(received);
			file.delete();
			hidden.delete();
		}

	}

	@SpringBootApplication
	static class FileSourceApplication {

		public static void main(String[] args) {
			SpringApplication.run(FileSourceApplication.class, args);
		}

	}


}
